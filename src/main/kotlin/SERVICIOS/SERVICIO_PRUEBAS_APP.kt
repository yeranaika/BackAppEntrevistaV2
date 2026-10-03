package SERVICIOS

import CONFIGURACION.HISTORIAL_PRUEBAS_LIMITE
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import MODELOS.CategoriaHabilidad
import MODELOS.IntentoNivelacion
import MODELOS.ModoPractica
import MODELOS.RepositorioNivelacion
import MODELOS.RepositorioPractica
import MODELOS.RepositorioSesionEntrevista
import MODELOS.ResultadoNivelacion
import MODELOS.ResumenPrueba
import MODELOS.SesionEntrevista
import MODELOS.SesionPractica
import java.util.UUID

/** Tipos de prueba que pide la app en /api/prueba-practica/front. */
enum class TipoPruebaApp(val codigos: Set<String>) {
    ENTREVISTA(setOf("ENT", "MIX", "SIM")),
    PRACTICA_TECNICA(setOf("PR")),
    PRACTICA_BLANDA(setOf("BL")),
    NIVELACION(setOf("NV", "NIV"));

    companion object {
        fun desdeCodigo(codigo: String?): TipoPruebaApp? {
            val normalizado = codigo?.trim()?.uppercase() ?: return ENTREVISTA
            return entries.firstOrNull { normalizado in it.codigos }
        }
    }
}

/** Lo que pide la app al crear cualquier prueba. */
data class PedidoPruebaApp(
    val nombreCargo: String?,
    val nivel: String?,
    val cantidadPreguntas: Int?
)

/** Una prueba creada por la app; cada tipo vive en su propio servicio. */
sealed interface PruebaApp {
    data class Entrevista(val sesion: SesionEntrevista) : PruebaApp
    data class Practica(val sesion: SesionPractica) : PruebaApp
    data class Nivelacion(val intento: IntentoNivelacion, val resultado: ResultadoNivelacion? = null) : PruebaApp
}

/**
 * Puerta única de la app Android: /api/prueba-practica crea y recibe las respuestas de entrevistas,
 * prácticas y nivelaciones por la misma ruta. Este servicio decide a cuál corresponde cada prueba.
 */
class ServicioPruebasApp(
    private val entrevistas: ServicioEntrevista,
    private val practicas: ServicioPractica,
    private val nivelaciones: ServicioNivelacion,
    private val sesionesEntrevista: RepositorioSesionEntrevista,
    private val repositorioPracticas: RepositorioPractica,
    private val repositorioNivelaciones: RepositorioNivelacion
) {

    suspend fun crear(usuarioId: UUID, tipoPrueba: String?, pedido: PedidoPruebaApp): PruebaApp =
        when (TipoPruebaApp.desdeCodigo(tipoPrueba) ?: throw tipoNoSoportado()) {
            // La app no retoma entrevistas: si quedó una a medias, se reemplaza.
            TipoPruebaApp.ENTREVISTA -> PruebaApp.Entrevista(
                entrevistas.iniciar(usuarioId, PedidoEntrevista(nombreCargo = pedido.nombreCargo, nivel = pedido.nivel, cantidadPreguntas = pedido.cantidadPreguntas), reemplazarEnProgreso = true)
            )
            TipoPruebaApp.PRACTICA_TECNICA -> PruebaApp.Practica(practicas.iniciar(usuarioId, pedidoPractica(pedido, CategoriaHabilidad.TECNICA)))
            TipoPruebaApp.PRACTICA_BLANDA -> PruebaApp.Practica(practicas.iniciar(usuarioId, pedidoPractica(pedido, CategoriaHabilidad.BLANDA)))
            // La nivelación recorre todos los niveles: el nivel que manda la app no aplica.
            TipoPruebaApp.NIVELACION -> PruebaApp.Nivelacion(nivelaciones.iniciar(usuarioId, cargoId = null, nombreCargo = pedido.nombreCargo))
        }

    /** Guarda las respuestas y cierra la prueba, sea del tipo que sea. Las que la app dejó en blanco no llegan aquí. */
    suspend fun responder(usuarioId: UUID, pruebaId: UUID, respuestas: List<RespuestaPrueba>): PruebaApp {
        entrevistas.buscarPropia(usuarioId, pruebaId)?.let {
            val deEntrevista = respuestas.map { r -> RespuestaUsuario(uuidDePregunta(r.preguntaServidaId), r.texto, r.opcionId) }
            return PruebaApp.Entrevista(entrevistas.responderYFinalizar(usuarioId, pruebaId, deEntrevista))
        }
        practicas.buscarPropia(usuarioId, pruebaId)?.let {
            return PruebaApp.Practica(practicas.responderYFinalizar(usuarioId, pruebaId, respuestas))
        }
        nivelaciones.buscarPropio(usuarioId, pruebaId)?.let {
            val (intento, resultado) = nivelaciones.responder(usuarioId, pruebaId, respuestas)
            return PruebaApp.Nivelacion(intento, resultado)
        }
        throw ErrorNoEncontrado("prueba_no_encontrada", "La prueba no existe")
    }

    /** Últimas pruebas de todos los tipos, de la más reciente a la más antigua. */
    suspend fun historial(usuarioId: UUID): List<ResumenPrueba> =
        (sesionesEntrevista.listarResumenes(usuarioId, HISTORIAL_PRUEBAS_LIMITE) +
            repositorioPracticas.listarResumenes(usuarioId, HISTORIAL_PRUEBAS_LIMITE) +
            repositorioNivelaciones.listarResumenes(usuarioId, HISTORIAL_PRUEBAS_LIMITE))
            .sortedByDescending { it.fechaInicio }
            .take(HISTORIAL_PRUEBAS_LIMITE)

    private fun pedidoPractica(pedido: PedidoPruebaApp, categoria: CategoriaHabilidad) = PedidoPractica(
        nombreCargo = pedido.nombreCargo,
        categoria = categoria.valorBd,
        modo = ModoPractica.MIXTO.valorBd,
        nivel = pedido.nivel,
        cantidadPreguntas = pedido.cantidadPreguntas
    )

    private fun uuidDePregunta(id: String): UUID =
        runCatching { UUID.fromString(id) }.getOrNull() ?: throw ErrorValidacion("pregunta_sesion_id_invalido", "El id de la pregunta no es válido")

    private fun tipoNoSoportado() =
        ErrorValidacion("tipo_prueba_no_soportado", "El tipo de prueba debe ser ENT (entrevista), PR o BL (práctica) o NV (nivelación)")
}
