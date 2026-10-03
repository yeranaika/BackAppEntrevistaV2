package VISTAS

import CONFIGURACION.INTENTOS_MAXIMOS_REPORTE
import ESQUEMAS.RespuestaFeedbackPregunta
import ESQUEMAS.RespuestaProgresoSkill
import ESQUEMAS.RespuestaPuntoProgreso
import ESQUEMAS.RespuestaRecomendacionSkill
import ESQUEMAS.RespuestaReporteEntrevista
import ESQUEMAS.RespuestaResumenReporte
import ESQUEMAS.RespuestaSkillReporte
import MODELOS.EstadoReporte
import MODELOS.PreguntaSesion
import MODELOS.ReporteEntrevista
import MODELOS.ResumenReporte
import MODELOS.SesionEntrevista
import MODELOS.TipoPregunta
import SERVICIOS.ProgresoSkill
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

private const val MENSAJE_ERROR_REPORTE = "No pudimos generar tu reporte. Puedes volver a intentarlo."

/** Sin reporte todavía (se está creando en segundo plano) se informa como "generando". */
fun SesionEntrevista.aReporte(reporte: ReporteEntrevista?): RespuestaReporteEntrevista {
    val estado = reporte?.estado ?: EstadoReporte.GENERANDO
    val listo = estado == EstadoReporte.LISTO
    return RespuestaReporteEntrevista(
        sesionId = id.toString(),
        estado = estado.valorBd,
        cargo = cargoObjetivo,
        nivel = nivel.valorBd,
        fechaEntrevista = fechaInicio.toString(),
        puntajeGlobal = reporte?.puntajeGlobal?.toDouble()?.takeIf { listo },
        puntajeTecnico = reporte?.puntajeTecnico?.toDouble()?.takeIf { listo },
        puntajeBlando = reporte?.puntajeBlando?.toDouble()?.takeIf { listo },
        puntajeLenguajeCorporal = reporte?.puntajeLenguajeCorporal?.toDouble()?.takeIf { listo },
        fortalezas = reporte?.fortalezas.orEmpty(),
        areasMejora = reporte?.areasMejora.orEmpty(),
        recomendaciones = reporte?.recomendaciones.orEmpty().map { RespuestaRecomendacionSkill(it.skillId, it.nombre, it.puntaje, it.nivelRequerido, it.prioridad) },
        resumen = reporte?.resumen,
        modoEvaluacion = reporte?.modo?.valorBd?.takeIf { listo },
        // El código interno del error queda en la BD; al usuario solo un mensaje claro.
        error = MENSAJE_ERROR_REPORTE.takeIf { estado == EstadoReporte.ERROR },
        puedeReintentar = estado == EstadoReporte.ERROR && (reporte?.intentos ?: 0) < INTENTOS_MAXIMOS_REPORTE,
        skills = reporte?.detalles.orEmpty().map {
            RespuestaSkillReporte(it.skillId?.toString(), it.nombre, it.categoria.valorBd, it.puntaje.toDouble(), it.nivelEvaluado?.valorBd, it.observacion, it.preguntasRespondidas)
        },
        preguntas = if (listo) preguntas.map { it.aFeedback() } else emptyList(),
        fechaGeneracion = reporte?.fecha?.toString()?.takeIf { estado != EstadoReporte.GENERANDO }
    )
}

private fun PreguntaSesion.aFeedback(): RespuestaFeedbackPregunta {
    val esAlternativa = tipo == TipoPregunta.OPCION_MULTIPLE
    val respuesta = if (esAlternativa) opciones.firstOrNull { it.id == opcionElegidaId }?.texto else transcripcion
    return RespuestaFeedbackPregunta(
        preguntaSesionId = id.toString(),
        orden = orden,
        enunciado = enunciado,
        tipo = tipo.valorBd,
        categoria = categoria.valorBd,
        respondida = estaRespondida,
        respuesta = respuesta,
        puntaje = when {
            !estaRespondida -> 0.0
            esAlternativa -> puntaje?.toDouble()
            else -> feedback?.get("puntaje")?.let { (it as? JsonPrimitive)?.doubleOrNull }
        },
        esCorrecta = if (esAlternativa && estaRespondida) opcionElegidaId == opcionCorrecta?.id else null,
        observacion = if (esAlternativa) opciones.firstOrNull { it.id == opcionElegidaId }?.explicacion
            else (feedback?.get("observacion") as? JsonPrimitive)?.contentOrNull,
        mejoras = (feedback?.get("mejoras") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        respuestaIdeal = if (esAlternativa) opcionCorrecta?.texto else respuestaIdeal
    )
}

fun ResumenReporte.aRespuesta() = RespuestaResumenReporte(
    sesionId = sesionId.toString(),
    cargo = cargoObjetivo,
    nivel = nivel.valorBd,
    fechaEntrevista = fechaEntrevista.toString(),
    estado = estado.valorBd,
    puntajeGlobal = puntajeGlobal.toDouble().takeIf { estado == EstadoReporte.LISTO },
    fechaGeneracion = fecha.toString()
)

fun ProgresoSkill.aRespuesta() = RespuestaProgresoSkill(
    skillId = nivel.skillId.toString(),
    nombre = nombre,
    nivel = nivel.nivel?.valorBd,
    puntaje = nivel.puntaje.toDouble(),
    evaluaciones = nivel.evaluaciones,
    historial = historial.map { RespuestaPuntoProgreso(it.sesionId.toString(), it.puntaje.toDouble(), it.fecha.toString()) }
)
