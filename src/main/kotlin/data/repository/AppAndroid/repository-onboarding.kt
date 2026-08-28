package data.repository.AppAndroid

import data.tables.usuarios.ObjetivoCarreraTable
import data.tables.usuarios.ProfileTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private suspend fun <T> tx(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

data class OnboardingData(val area: String, val metaCargo: String, val nivel: String)

/** Persistencia exclusiva del perfil y objetivo del usuario. */
class OnboardingRepository {
    suspend fun guardarObjetivo(
        usuarioId: UUID,
        area: String,
        metaCargo: String,
        nivel: String
    ) = tx {
        val ahora = OffsetDateTime.now(ZoneOffset.UTC)
        val perfiles = ProfileTable.update({ ProfileTable.usuarioId eq usuarioId }) {
            it[ProfileTable.area] = area
            it[ProfileTable.nivelExperiencia] = nivel
            it[ProfileTable.fechaActualizacion] = ahora
        }
        if (perfiles == 0) {
            ProfileTable.insert {
                it[ProfileTable.perfilId] = UUID.randomUUID()
                it[ProfileTable.usuarioId] = usuarioId
                it[ProfileTable.area] = area
                it[ProfileTable.nivelExperiencia] = nivel
                it[ProfileTable.fechaActualizacion] = ahora
            }
        }

        val objetivos = ObjetivoCarreraTable.update({
            (ObjetivoCarreraTable.usuarioId eq usuarioId) and (ObjetivoCarreraTable.activo eq true)
        }) {
            it[ObjetivoCarreraTable.nombreCargo] = metaCargo
            it[ObjetivoCarreraTable.sector] = area
        }
        if (objetivos == 0) {
            ObjetivoCarreraTable.insert {
                it[ObjetivoCarreraTable.usuarioId] = usuarioId
                it[ObjetivoCarreraTable.nombreCargo] = metaCargo
                it[ObjetivoCarreraTable.sector] = area
            }
        }
    }

    suspend fun obtenerOnboarding(usuarioId: UUID): OnboardingData? = tx {
        val perfil = ProfileTable.selectAll()
            .where { ProfileTable.usuarioId eq usuarioId }
            .singleOrNull()
        val objetivo = ObjetivoCarreraTable.selectAll()
            .where { (ObjetivoCarreraTable.usuarioId eq usuarioId) and (ObjetivoCarreraTable.activo eq true) }
            .singleOrNull()
        if (perfil == null || objetivo == null) return@tx null

        OnboardingData(
            area = perfil[ProfileTable.area] ?: return@tx null,
            metaCargo = objetivo[ObjetivoCarreraTable.nombreCargo],
            nivel = perfil[ProfileTable.nivelExperiencia] ?: "jr"
        )
    }
}
