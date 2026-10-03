package MODELOS

import ERRORES.ErrorConflicto
import UTILIDADES.SQLSTATE_VALOR_DUPLICADO
import UTILIDADES.transaccion
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.time.Instant
import java.util.UUID

const val ESTADO_SUSCRIPCION_ACTIVA = "activa"
const val PLAN_GRATUITO = "free"
const val PLAN_PREMIUM = "premium"
const val PROVEEDOR_GOOGLE_PLAY = "google_play"
const val PROVEEDOR_CODIGO = "codigo"

data class Suscripcion(
    val plan: String,
    val proveedor: String?,
    val estado: String,
    val inicio: Instant,
    val expiracion: Instant?
)

data class CodigoSuscripcion(
    val codigo: String,
    val expiracion: Instant?,
    val maxUsos: Int,
    val tipoLicencia: String
)

data class NuevoCodigo(
    val codigo: String,
    val etiqueta: String?,
    val duracionDias: Int,
    val maxUsos: Int,
    val expiracion: Instant?,
    val tipoLicencia: String
)

interface RepositorioSuscripcion {
    suspend fun listarDeUsuario(usuarioId: UUID): List<Suscripcion>

    /**
     * Activa el plan comprado en Google Play. Lanza 409 si ese token ya activó otra cuenta:
     * un mismo pago no puede dar premium a varios usuarios.
     */
    suspend fun registrarCompraGoogle(usuarioId: UUID, plan: String, expiracion: Instant?, hashToken: String)

    suspend fun crearCodigo(nuevo: NuevoCodigo): CodigoSuscripcion

    /**
     * Canjea el código si sigue vigente y con usos disponibles, y suma sus días al plan premium.
     * Todo en una transacción y con un UPDATE condicional: dos canjes simultáneos no superan max_usos.
     * Devuelve false si el código no existe, venció o se agotó.
     */
    suspend fun canjearCodigo(usuarioId: UUID, codigo: String, ahora: Instant): Boolean
}

class RepositorioSuscripcionExposed : RepositorioSuscripcion {

    override suspend fun listarDeUsuario(usuarioId: UUID): List<Suscripcion> = transaccion {
        TablaSuscripcion.selectAll().where { TablaSuscripcion.usuarioId eq usuarioId }
            .orderBy(TablaSuscripcion.fechaInicio to SortOrder.DESC)
            .map {
                Suscripcion(
                    plan = it[TablaSuscripcion.plan],
                    proveedor = it[TablaSuscripcion.proveedor],
                    estado = it[TablaSuscripcion.estado],
                    inicio = it[TablaSuscripcion.fechaInicio],
                    expiracion = it[TablaSuscripcion.fechaExpiracion]
                )
            }
    }

    override suspend fun registrarCompraGoogle(usuarioId: UUID, plan: String, expiracion: Instant?, hashToken: String) {
        try {
            registrarCompra(usuarioId, plan, expiracion, hashToken)
        } catch (e: ExposedSQLException) {
            // Dos cuentas registrando el mismo token a la vez: el índice único deja pasar solo a una.
            if (e.sqlState == SQLSTATE_VALOR_DUPLICADO) throw compraDeOtraCuenta()
            throw e
        }
    }

    private suspend fun registrarCompra(usuarioId: UUID, plan: String, expiracion: Instant?, hashToken: String) {
        transaccion {
            val duenoDelToken = TablaSuscripcion.selectAll().where { TablaSuscripcion.hashTokenCompra eq hashToken }
                .limit(1).firstOrNull()?.get(TablaSuscripcion.usuarioId)
            if (duenoDelToken != null && duenoDelToken != usuarioId) {
                throw compraDeOtraCuenta()
            }
            guardarPlan(usuarioId, plan, PROVEEDOR_GOOGLE_PLAY, expiracion, codigoId = null, hashToken = hashToken)
        }
    }

    override suspend fun crearCodigo(nuevo: NuevoCodigo): CodigoSuscripcion = transaccion {
        TablaCodigoSuscripcion.insert {
            it[codigo] = nuevo.codigo
            it[etiqueta] = nuevo.etiqueta
            it[duracionDias] = nuevo.duracionDias
            it[maxUsos] = nuevo.maxUsos
            it[usosRealizados] = 0
            it[fechaCreacion] = Instant.now()
            it[fechaExpiracion] = nuevo.expiracion
            it[activo] = true
        }
        CodigoSuscripcion(nuevo.codigo, nuevo.expiracion, nuevo.maxUsos, nuevo.tipoLicencia)
    }

    override suspend fun canjearCodigo(usuarioId: UUID, codigo: String, ahora: Instant): Boolean = transaccion {
        val vigente = (TablaCodigoSuscripcion.codigo eq codigo) and
            (TablaCodigoSuscripcion.activo eq true) and
            (TablaCodigoSuscripcion.usosRealizados less TablaCodigoSuscripcion.maxUsos) and
            (TablaCodigoSuscripcion.fechaExpiracion.isNull() or (TablaCodigoSuscripcion.fechaExpiracion greaterEq ahora))
        val fila = TablaCodigoSuscripcion.selectAll().where { vigente }.limit(1).firstOrNull() ?: return@transaccion false

        // La condición se repite en el UPDATE: si otro canje ganó el último uso, aquí se actualizan 0 filas.
        val consumidas = TablaCodigoSuscripcion.update({ vigente }) {
            it[usosRealizados] = usosRealizados + 1
        }
        if (consumidas == 0) return@transaccion false
        TablaCodigoSuscripcion.update({
            (TablaCodigoSuscripcion.codigo eq codigo) and (TablaCodigoSuscripcion.usosRealizados greaterEq TablaCodigoSuscripcion.maxUsos)
        }) { it[activo] = false }

        val expiracionActual = TablaSuscripcion.selectAll()
            .where { (TablaSuscripcion.usuarioId eq usuarioId) and (TablaSuscripcion.plan eq PLAN_PREMIUM) }
            .limit(1).firstOrNull()?.get(TablaSuscripcion.fechaExpiracion)
        // Los días se suman a lo que le quede de premium, no desde hoy.
        val base = expiracionActual?.takeIf { it.isAfter(ahora) } ?: ahora
        val nuevaExpiracion = base.plus(Duration.ofDays(fila[TablaCodigoSuscripcion.duracionDias].toLong()))
        guardarPlan(usuarioId, PLAN_PREMIUM, PROVEEDOR_CODIGO, nuevaExpiracion, fila[TablaCodigoSuscripcion.id].value, hashToken = null)
        true
    }

    private fun compraDeOtraCuenta() = ErrorConflicto("compra_ya_registrada", "Esta compra ya está asociada a otra cuenta")

    /** Una fila por (usuario, plan): se crea o se renueva. Debe llamarse dentro de una transacción. */
    private fun guardarPlan(usuarioId: UUID, plan: String, proveedor: String, expiracion: Instant?, codigoId: UUID?, hashToken: String?) {
        val ahora = Instant.now()
        val delPlan = (TablaSuscripcion.usuarioId eq usuarioId) and (TablaSuscripcion.plan eq plan)
        val renovadas = TablaSuscripcion.update({ delPlan }) {
            it[TablaSuscripcion.proveedor] = proveedor
            it[estado] = ESTADO_SUSCRIPCION_ACTIVA
            it[fechaRenovacion] = ahora
            it[fechaExpiracion] = expiracion
            it[TablaSuscripcion.codigoId] = codigoId
            if (hashToken != null) it[hashTokenCompra] = hashToken
        }
        if (renovadas == 0) {
            TablaSuscripcion.insert {
                it[TablaSuscripcion.usuarioId] = usuarioId
                it[TablaSuscripcion.plan] = plan
                it[TablaSuscripcion.proveedor] = proveedor
                it[estado] = ESTADO_SUSCRIPCION_ACTIVA
                it[fechaInicio] = ahora
                it[fechaExpiracion] = expiracion
                it[TablaSuscripcion.codigoId] = codigoId
                it[hashTokenCompra] = hashToken
            }
        }
    }
}
