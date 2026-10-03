package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

interface RepositorioRecuperacionContrasena {
    /** Guarda un código nuevo e invalida los anteriores del usuario: solo el último sirve. */
    suspend fun crear(usuarioId: UUID, codigo: String, emitidoEn: Instant, expiraEn: Instant)

    suspend fun buscarVigente(usuarioId: UUID, ahora: Instant): CodigoRecuperacion?

    suspend fun registrarIntentoFallido(token: UUID)

    /** Marca el código como usado solo si seguía sin usar; true = esta llamada lo consumió. */
    suspend fun consumir(token: UUID): Boolean

    suspend fun invalidar(token: UUID)
}

class RepositorioRecuperacionContrasenaExposed : RepositorioRecuperacionContrasena {

    override suspend fun crear(usuarioId: UUID, codigo: String, emitidoEn: Instant, expiraEn: Instant) {
        transaccion {
            TablaRecuperacionContrasena.update({
                (TablaRecuperacionContrasena.usuarioId eq usuarioId) and (TablaRecuperacionContrasena.usado eq false)
            }) { it[usado] = true }

            TablaRecuperacionContrasena.insert {
                it[token] = UUID.randomUUID()
                it[TablaRecuperacionContrasena.usuarioId] = usuarioId
                it[TablaRecuperacionContrasena.codigo] = codigo
                it[TablaRecuperacionContrasena.emitidoEn] = emitidoEn
                it[TablaRecuperacionContrasena.expiraEn] = expiraEn
                it[usado] = false
                it[intentosFallidos] = 0
            }
        }
    }

    override suspend fun buscarVigente(usuarioId: UUID, ahora: Instant): CodigoRecuperacion? = transaccion {
        TablaRecuperacionContrasena
            .selectAll()
            .where {
                (TablaRecuperacionContrasena.usuarioId eq usuarioId) and
                    (TablaRecuperacionContrasena.usado eq false) and
                    (TablaRecuperacionContrasena.expiraEn greater ahora)
            }
            .orderBy(TablaRecuperacionContrasena.emitidoEn to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.let {
                CodigoRecuperacion(
                    token = it[TablaRecuperacionContrasena.token],
                    usuarioId = it[TablaRecuperacionContrasena.usuarioId],
                    codigo = it[TablaRecuperacionContrasena.codigo],
                    expiraEn = it[TablaRecuperacionContrasena.expiraEn],
                    intentosFallidos = it[TablaRecuperacionContrasena.intentosFallidos].toInt()
                )
            }
    }

    override suspend fun registrarIntentoFallido(token: UUID) {
        transaccion {
            // Incremento en SQL: dos intentos simultáneos no se pisan.
            TablaRecuperacionContrasena.update({ TablaRecuperacionContrasena.token eq token }) {
                it[intentosFallidos] = intentosFallidos + 1.toShort()
            }
        }
    }

    override suspend fun consumir(token: UUID): Boolean = transaccion {
        TablaRecuperacionContrasena.update({
            (TablaRecuperacionContrasena.token eq token) and (TablaRecuperacionContrasena.usado eq false)
        }) { it[usado] = true } == 1
    }

    override suspend fun invalidar(token: UUID) {
        transaccion {
            TablaRecuperacionContrasena.update({ TablaRecuperacionContrasena.token eq token }) { it[usado] = true }
        }
    }
}
