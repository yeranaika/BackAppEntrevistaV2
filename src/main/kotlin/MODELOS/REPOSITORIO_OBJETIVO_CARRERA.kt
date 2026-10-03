package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

interface RepositorioObjetivoCarrera {
    suspend fun buscarActivo(usuarioId: UUID): ObjetivoCarrera?

    /** Desactiva el objetivo actual y crea uno nuevo (queda el historial). */
    suspend fun reemplazarActivo(usuarioId: UUID, nombreCargo: String, sector: String?): ObjetivoCarrera

    /** Devuelve false si no había objetivo activo. */
    suspend fun desactivar(usuarioId: UUID): Boolean
}

class RepositorioObjetivoCarreraExposed : RepositorioObjetivoCarrera {

    override suspend fun buscarActivo(usuarioId: UUID): ObjetivoCarrera? = transaccion {
        TablaObjetivoCarrera
            .selectAll()
            .where { (TablaObjetivoCarrera.usuarioId eq usuarioId) and (TablaObjetivoCarrera.activo eq true) }
            .limit(1)
            .firstOrNull()
            ?.let {
                ObjetivoCarrera(
                    id = it[TablaObjetivoCarrera.id].value,
                    nombreCargo = it[TablaObjetivoCarrera.nombreCargo],
                    sector = it[TablaObjetivoCarrera.sector]
                )
            }
    }

    override suspend fun reemplazarActivo(usuarioId: UUID, nombreCargo: String, sector: String?): ObjetivoCarrera =
        transaccion {
            desactivarActivo(usuarioId)
            val id = TablaObjetivoCarrera.insertAndGetId {
                it[TablaObjetivoCarrera.usuarioId] = usuarioId
                it[TablaObjetivoCarrera.nombreCargo] = nombreCargo
                it[TablaObjetivoCarrera.sector] = sector
                it[activo] = true
            }.value
            ObjetivoCarrera(id, nombreCargo, sector)
        }

    override suspend fun desactivar(usuarioId: UUID): Boolean = transaccion { desactivarActivo(usuarioId) > 0 }

    private fun desactivarActivo(usuarioId: UUID): Int =
        TablaObjetivoCarrera.update({
            (TablaObjetivoCarrera.usuarioId eq usuarioId) and (TablaObjetivoCarrera.activo eq true)
        }) {
            it[activo] = false
        }
}
