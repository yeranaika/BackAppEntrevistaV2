package SERVICIOS

import ERRORES.ErrorNoEncontrado
import MODELOS.CambiosPerfil
import MODELOS.NivelExperiencia
import MODELOS.ObjetivoCarrera
import MODELOS.RepositorioObjetivoCarrera
import MODELOS.RepositorioPerfil
import UTILIDADES.sinVacios
import UTILIDADES.validarArea
import UTILIDADES.validarCargo
import UTILIDADES.validarNivel
import UTILIDADES.validarSector
import java.util.UUID

data class ResumenOnboarding(
    val area: String,
    val nivelExperiencia: NivelExperiencia,
    val nombreCargo: String,
    val descripcionObjetivo: String?
)

/**
 * Meta profesional del usuario: área y nivel (perfil) + cargo al que aspira (objetivo activo).
 * Único camino de escritura para /onboarding, /perfil/objetivo y /me/objetivo.
 */
class ServicioOnboarding(
    private val perfiles: RepositorioPerfil,
    private val objetivos: RepositorioObjetivoCarrera
) {

    suspend fun guardarOnboarding(
        usuarioId: UUID,
        area: String,
        nivel: String,
        nombreCargo: String,
        descripcionObjetivo: String? = null
    ): ResumenOnboarding {
        val areaValida = validarArea(area)
        val nivelValido = validarNivel(nivel)
        val cargoValido = validarCargo(nombreCargo)
        val descripcion = descripcionObjetivo.sinVacios()

        perfiles.guardar(
            usuarioId,
            CambiosPerfil(nivelExperiencia = nivelValido, area = areaValida, notaObjetivos = descripcion)
        )
        objetivos.reemplazarActivo(usuarioId, cargoValido, sector = areaValida)
        return ResumenOnboarding(areaValida, nivelValido, cargoValido, descripcion)
    }

    /** null si al usuario le falta el área, el nivel o el objetivo. */
    suspend fun obtenerOnboarding(usuarioId: UUID): ResumenOnboarding? {
        val perfil = perfiles.buscarPorUsuario(usuarioId) ?: return null
        val objetivo = objetivos.buscarActivo(usuarioId) ?: return null
        val area = perfil.area.sinVacios() ?: return null
        val nivel = perfil.nivelExperiencia ?: return null
        return ResumenOnboarding(area, nivel, objetivo.nombreCargo, perfil.notaObjetivos)
    }

    suspend fun obtenerObjetivo(usuarioId: UUID): ObjetivoCarrera =
        objetivos.buscarActivo(usuarioId) ?: throw objetivoNoEncontrado()

    suspend fun guardarObjetivo(usuarioId: UUID, nombreCargo: String, sector: String?): ObjetivoCarrera =
        objetivos.reemplazarActivo(usuarioId, validarCargo(nombreCargo), sector.sinVacios()?.let(::validarSector))

    suspend fun eliminarObjetivo(usuarioId: UUID) {
        if (!objetivos.desactivar(usuarioId)) throw objetivoNoEncontrado()
    }

    private fun objetivoNoEncontrado() = ErrorNoEncontrado("objetivo_not_found", "El usuario no tiene un objetivo activo")
}
