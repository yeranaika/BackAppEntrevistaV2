package SERVICIOS

import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import MODELOS.Cargo
import MODELOS.LectorMercado
import MODELOS.NivelExperiencia
import MODELOS.RepositorioObjetivoCarrera
import MODELOS.RepositorioPerfil
import java.util.UUID

private const val LARGO_MAXIMO_NOMBRE_CARGO = 120

/** Cargo a practicar: del catálogo (null si no está) y el nombre que se guarda. */
data class CargoPrueba(val cargo: Cargo?, val nombre: String)

/**
 * Completa lo que el usuario no indicó al iniciar una entrevista, práctica o nivelación:
 * el cargo sale de su objetivo del onboarding y el nivel de su perfil.
 */
class ResolutorContextoPrueba(
    private val mercado: LectorMercado,
    private val perfiles: RepositorioPerfil,
    private val objetivos: RepositorioObjetivoCarrera
) {

    /** Cargo pedido por id, por nombre o, si no viene ninguno, el objetivo activo del usuario. */
    suspend fun cargo(usuarioId: UUID, cargoId: String?, nombreCargo: String?): CargoPrueba {
        cargoId?.let { texto ->
            val id = runCatching { UUID.fromString(texto) }.getOrNull()
                ?: throw ErrorValidacion("cargo_id_invalido", "El id del cargo no es válido")
            val cargo = mercado.buscarCargo(id)?.takeIf { it.estaActivo }
                ?: throw ErrorNoEncontrado("cargo_no_encontrado", "El cargo no existe")
            return CargoPrueba(cargo, cargo.nombre)
        }
        val nombre = nombreCargo?.trim()?.takeIf { it.isNotEmpty() }
            ?: objetivos.buscarActivo(usuarioId)?.nombreCargo
            ?: throw ErrorValidacion("cargo_requerido", "Indica el cargo o define tu objetivo en el onboarding")
        if (nombre.length > LARGO_MAXIMO_NOMBRE_CARGO) {
            throw ErrorValidacion("cargo_invalido", "El cargo admite hasta $LARGO_MAXIMO_NOMBRE_CARGO caracteres")
        }
        // Un cargo que no está en el catálogo igual se puede practicar, con preguntas generales.
        val cargo = mercado.buscarCargoPorNombre(nombre)?.takeIf { it.estaActivo }
        return CargoPrueba(cargo, cargo?.nombre ?: nombre)
    }

    /** Nivel pedido; si no viene, el del perfil, el base del cargo o junior. */
    suspend fun nivel(usuarioId: UUID, nivel: String?, cargo: Cargo?): NivelExperiencia {
        if (!nivel.isNullOrBlank()) {
            return NivelExperiencia.desdeTexto(nivel)
                ?: throw ErrorValidacion("nivel_invalido", "El nivel debe ser junior, semisenior o senior")
        }
        return perfiles.buscarPorUsuario(usuarioId)?.nivelExperiencia ?: cargo?.nivelBase ?: NivelExperiencia.JUNIOR
    }
}
