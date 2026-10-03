package PRUEBAS.DOBLES

import MODELOS.CategoriaHabilidad
import MODELOS.ContenidoPregunta
import MODELOS.EstadoPregunta
import MODELOS.NivelExperiencia
import MODELOS.NuevaOpcion
import MODELOS.Pregunta
import MODELOS.SesionEntrevista
import MODELOS.TipoPregunta
import SERVICIOS.ProcesadorEntrevistaFinalizada
import kotlinx.coroutines.runBlocking
import java.util.UUID

/** Guarda las entrevistas que se le pidió procesar (en vez de generar el reporte). */
class ProcesadorEntrevistaGrabador : ProcesadorEntrevistaFinalizada {
    val procesadas = mutableListOf<SesionEntrevista>()
    var error: Exception? = null

    override suspend fun procesar(sesion: SesionEntrevista) {
        error?.let { throw it }
        procesadas += sesion
    }
}

/** Inserta una pregunta APROBADA en el banco. */
fun SistemaPrueba.sembrarPregunta(
    categoria: CategoriaHabilidad = CategoriaHabilidad.TECNICA,
    nivel: NivelExperiencia = NivelExperiencia.JUNIOR,
    tipo: TipoPregunta = if (categoria == CategoriaHabilidad.TECNICA) TipoPregunta.OPCION_MULTIPLE else TipoPregunta.ABIERTA_TEXTO,
    cargoId: UUID? = null,
    skillId: UUID? = null,
    enunciado: String = "Pregunta ${UUID.randomUUID()}",
    estado: EstadoPregunta = EstadoPregunta.APROBADA
): Pregunta = runBlocking {
    val opciones = if (tipo == TipoPregunta.OPCION_MULTIPLE) {
        listOf(NuevaOpcion("Correcta", true, null), NuevaOpcion("Incorrecta A", false, null), NuevaOpcion("Incorrecta B", false, null))
    } else {
        emptyList()
    }
    preguntas.crear(
        ContenidoPregunta(skillId, cargoId, tipo, categoria, nivel, enunciado, "Respuesta ideal", null, opciones),
        estado
    )
}

/** Banco mínimo para una entrevista general de nivel junior: [tecnicas] de opción múltiple + [blandas] abiertas. */
fun SistemaPrueba.sembrarBancoGeneral(tecnicas: Int = 5, blandas: Int = 3, nivel: NivelExperiencia = NivelExperiencia.JUNIOR) {
    repeat(tecnicas) { sembrarPregunta(CategoriaHabilidad.TECNICA, nivel) }
    repeat(blandas) { sembrarPregunta(CategoriaHabilidad.BLANDA, nivel) }
}

/** Premium a gusto de la prueba. */
class PremiumFalso(var esPremium: Boolean = false) : SERVICIOS.VerificadorPremium {
    override suspend fun esPremium(usuarioId: UUID): Boolean = esPremium
}

/**
 * LLM falso para evaluar entrevistas: lee los ids de las respuestas del mensaje de usuario y responde
 * según [modo]: "valido" (puntaje fijo), "ids_incorrectos", "json_roto" o "caido" (503).
 */
class ProveedorEvaluacionFalso(var modo: String = "valido", private val puntaje: Int = 90) : INTEGRACIONES.ProveedorPreguntasIa {
    val solicitudes = mutableListOf<INTEGRACIONES.SolicitudProveedorIa>()

    override suspend fun generar(solicitud: INTEGRACIONES.SolicitudProveedorIa): INTEGRACIONES.RespuestaProveedorIa {
        solicitudes += solicitud
        if (modo == "caido") throw ERRORES.ErrorServicioExterno("provider_not_configured")
        if (modo == "json_roto") return INTEGRACIONES.RespuestaProveedorIa("{no es json", 10, 5)
        val datos = solicitud.instruccionUsuario.substringAfter("\n")
        val ids = kotlinx.serialization.json.Json.parseToJsonElement(datos).let { it as kotlinx.serialization.json.JsonObject }["respuestas"]
            .let { it as kotlinx.serialization.json.JsonArray }
            .map { (it as kotlinx.serialization.json.JsonObject)["id"].toString().trim('"') }
            .let { if (modo == "ids_incorrectos") it.map { UUID.randomUUID().toString() } else it }
        val evaluaciones = ids.joinToString(",") { """{"id":"$it","puntaje":$puntaje,"observacion":"Buena respuesta","mejoras":["Da un ejemplo concreto"]}""" }
        val contenido = """{"evaluaciones":[$evaluaciones],"fortalezas":["Comunicación clara"],"areas_mejora":["Profundizar en arquitectura"],"resumen":"Resumen de la IA."}"""
        return INTEGRACIONES.RespuestaProveedorIa(contenido, 1000, 300)
    }
}
