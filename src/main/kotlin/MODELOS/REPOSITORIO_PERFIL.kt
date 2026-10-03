package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

interface RepositorioPerfil {
    suspend fun buscarPorUsuario(usuarioId: UUID): Perfil?

    /** Crea el perfil o actualiza solo los campos no nulos de [cambios]. */
    suspend fun guardar(usuarioId: UUID, cambios: CambiosPerfil)
}

class RepositorioPerfilExposed : RepositorioPerfil {

    override suspend fun buscarPorUsuario(usuarioId: UUID): Perfil? = transaccion {
        TablaPerfil.selectAll().where { TablaPerfil.usuarioId eq usuarioId }.limit(1).firstOrNull()?.aPerfil()
    }

    override suspend fun guardar(usuarioId: UUID, cambios: CambiosPerfil) {
        transaccion {
            val actualizadas = TablaPerfil.update({ TablaPerfil.usuarioId eq usuarioId }) { aplicar(it, cambios) }
            if (actualizadas == 0) {
                TablaPerfil.insert {
                    it[perfilId] = UUID.randomUUID()
                    it[TablaPerfil.usuarioId] = usuarioId
                    aplicar(it, cambios)
                }
            }
        }
    }

    private fun aplicar(fila: UpdateBuilder<*>, cambios: CambiosPerfil) {
        cambios.nivelExperiencia?.let { fila[TablaPerfil.nivelExperiencia] = it.valorBd }
        cambios.area?.let { fila[TablaPerfil.area] = it }
        cambios.pais?.let { fila[TablaPerfil.pais] = it }
        cambios.notaObjetivos?.let { fila[TablaPerfil.notaObjetivos] = it }
        cambios.flagsAccesibilidad?.let { fila[TablaPerfil.flagsAccesibilidad] = it }
        fila[TablaPerfil.fechaActualizacion] = OffsetDateTime.now()
    }

    private fun ResultRow.aPerfil() = Perfil(
        usuarioId = this[TablaPerfil.usuarioId],
        nivelExperiencia = NivelExperiencia.desdeBd(this[TablaPerfil.nivelExperiencia]),
        area = this[TablaPerfil.area],
        pais = this[TablaPerfil.pais],
        notaObjetivos = this[TablaPerfil.notaObjetivos],
        flagsAccesibilidad = this[TablaPerfil.flagsAccesibilidad]
    )
}
