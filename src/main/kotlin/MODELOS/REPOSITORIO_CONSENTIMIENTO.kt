package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

const val ALCANCE_ENTRENAMIENTO_IA = "ia_entrenamiento"

data class TextoConsentimiento(
    val version: String,
    val titulo: String,
    val cuerpo: String,
    val alcancesPosibles: List<String>,
    val estaVigente: Boolean,
    val fechaPublicacion: OffsetDateTime
)

data class Consentimiento(
    val id: UUID,
    val version: String,
    val alcancesAceptados: List<String>,
    val aceptaEntrenamientoIa: Boolean,
    val fechaOtorgado: Instant,
    val fechaRevocado: Instant?
)

interface RepositorioTextoConsentimiento {
    suspend fun vigente(): TextoConsentimiento?
    suspend fun buscar(version: String): TextoConsentimiento?
    suspend fun listar(): List<TextoConsentimiento>

    /** Deja [version] como la única vigente (la crea o actualiza su contenido). */
    suspend fun publicar(version: String, titulo: String, cuerpo: String): TextoConsentimiento
}

interface RepositorioConsentimiento {
    /** Revoca el consentimiento vigente del usuario y registra el nuevo, en una sola transacción. */
    suspend fun otorgar(usuarioId: UUID, version: String, aceptados: List<String>, ipOrigen: String?): Consentimiento
    suspend fun vigente(usuarioId: UUID): Consentimiento?

    /** false si no había uno vigente. */
    suspend fun revocarVigente(usuarioId: UUID): Boolean
}

class RepositorioTextoConsentimientoExposed : RepositorioTextoConsentimiento {

    override suspend fun vigente(): TextoConsentimiento? = transaccion {
        TablaTextoConsentimiento.selectAll().where { TablaTextoConsentimiento.vigente eq true }
            .orderBy(TablaTextoConsentimiento.fechaPublicacion to SortOrder.DESC)
            .limit(1).firstOrNull()?.aTexto()
    }

    override suspend fun buscar(version: String): TextoConsentimiento? = transaccion {
        TablaTextoConsentimiento.selectAll().where { TablaTextoConsentimiento.version eq version }.limit(1).firstOrNull()?.aTexto()
    }

    override suspend fun listar(): List<TextoConsentimiento> = transaccion {
        TablaTextoConsentimiento.selectAll().orderBy(TablaTextoConsentimiento.fechaPublicacion to SortOrder.DESC).map { it.aTexto() }
    }

    override suspend fun publicar(version: String, titulo: String, cuerpo: String): TextoConsentimiento = transaccion {
        val ahora = OffsetDateTime.now()
        TablaTextoConsentimiento.update({ TablaTextoConsentimiento.vigente eq true }) { it[vigente] = false }
        val actualizadas = TablaTextoConsentimiento.update({ TablaTextoConsentimiento.version eq version }) {
            it[TablaTextoConsentimiento.titulo] = titulo
            it[TablaTextoConsentimiento.cuerpo] = cuerpo
            it[vigente] = true
            it[fechaPublicacion] = ahora
        }
        if (actualizadas == 0) {
            TablaTextoConsentimiento.insert {
                it[TablaTextoConsentimiento.version] = version
                it[TablaTextoConsentimiento.titulo] = titulo
                it[TablaTextoConsentimiento.cuerpo] = cuerpo
                it[vigente] = true
                it[fechaPublicacion] = ahora
            }
        }
        TablaTextoConsentimiento.selectAll().where { TablaTextoConsentimiento.version eq version }.single().aTexto()
    }

    private fun ResultRow.aTexto() = TextoConsentimiento(
        version = this[TablaTextoConsentimiento.version],
        titulo = this[TablaTextoConsentimiento.titulo],
        cuerpo = this[TablaTextoConsentimiento.cuerpo],
        alcancesPosibles = this[TablaTextoConsentimiento.alcancesPosibles],
        estaVigente = this[TablaTextoConsentimiento.vigente],
        fechaPublicacion = this[TablaTextoConsentimiento.fechaPublicacion]
    )
}

class RepositorioConsentimientoExposed : RepositorioConsentimiento {

    override suspend fun otorgar(usuarioId: UUID, version: String, aceptados: List<String>, ipOrigen: String?): Consentimiento =
        transaccion {
            val ahora = Instant.now()
            revocar(usuarioId, ahora)
            val id = UUID.randomUUID()
            TablaConsentimiento.insert {
                it[TablaConsentimiento.id] = id
                it[TablaConsentimiento.usuarioId] = usuarioId
                it[TablaConsentimiento.version] = version
                it[alcancesAceptados] = aceptados
                // Solo con este permiso explícito sus videos pueden usarse para entrenar modelos.
                it[aceptaEntrenamientoIa] = ALCANCE_ENTRENAMIENTO_IA in aceptados
                it[fechaOtorgado] = ahora
                it[TablaConsentimiento.ipOrigen] = ipOrigen
            }
            Consentimiento(id, version, aceptados, ALCANCE_ENTRENAMIENTO_IA in aceptados, ahora, null)
        }

    override suspend fun vigente(usuarioId: UUID): Consentimiento? = transaccion {
        TablaConsentimiento.selectAll()
            .where { (TablaConsentimiento.usuarioId eq usuarioId) and TablaConsentimiento.fechaRevocado.isNull() }
            .orderBy(TablaConsentimiento.fechaOtorgado to SortOrder.DESC)
            .limit(1).firstOrNull()
            ?.let {
                Consentimiento(
                    id = it[TablaConsentimiento.id],
                    version = it[TablaConsentimiento.version],
                    alcancesAceptados = it[TablaConsentimiento.alcancesAceptados],
                    aceptaEntrenamientoIa = it[TablaConsentimiento.aceptaEntrenamientoIa],
                    fechaOtorgado = it[TablaConsentimiento.fechaOtorgado],
                    fechaRevocado = it[TablaConsentimiento.fechaRevocado]
                )
            }
    }

    override suspend fun revocarVigente(usuarioId: UUID): Boolean = transaccion { revocar(usuarioId, Instant.now()) > 0 }

    private fun revocar(usuarioId: UUID, ahora: Instant): Int =
        TablaConsentimiento.update({ (TablaConsentimiento.usuarioId eq usuarioId) and TablaConsentimiento.fechaRevocado.isNull() }) {
            it[fechaRevocado] = ahora
        }
}
