package VISTAS

import ESQUEMAS.ConfigRespuestaPruebaPractica
import ESQUEMAS.CorreccionPreguntaEntrevista
import ESQUEMAS.OpcionPruebaPractica
import ESQUEMAS.PreguntaPruebaPractica
import ESQUEMAS.RespuestaCrearPruebaPractica
import ESQUEMAS.RespuestaDadaEntrevista
import ESQUEMAS.RespuestaEnviarRespuestasPractica
import ESQUEMAS.RespuestaHistorialEntrevistas
import ESQUEMAS.RespuestaOpcionEntrevista
import ESQUEMAS.RespuestaPreguntaEntrevista
import ESQUEMAS.RespuestaSesionEntrevista
import ESQUEMAS.ResultadoPreguntaPractica
import CONFIGURACION.LARGO_MAXIMO_RESPUESTA_ENTREVISTA
import MODELOS.CategoriaHabilidad
import MODELOS.EstadoSesionEntrevista
import MODELOS.PaginaSesionesEntrevista
import MODELOS.PreguntaSesion
import MODELOS.ResumenSesionEntrevista
import MODELOS.SesionEntrevista
import MODELOS.TipoPregunta

// ---------- API /api/v1/entrevistas ----------

/** Con [conPreguntas] incluye el detalle de cada pregunta (sin revelar la corrección mientras la sesión sigue). */
fun SesionEntrevista.aRespuesta(conPreguntas: Boolean = true) = RespuestaSesionEntrevista(
    sesionId = id.toString(),
    cargoId = cargoId?.toString(),
    cargo = cargoObjetivo,
    nivel = nivel.valorBd,
    estado = estado.valorBd,
    fechaInicio = fechaInicio.toString(),
    fechaFin = fechaFin?.toString(),
    totalPreguntas = preguntas.size,
    respondidas = respondidas,
    preguntas = if (conPreguntas) preguntas.map { it.aRespuesta(mostrarCorreccion = estado != EstadoSesionEntrevista.EN_PROGRESO) } else emptyList()
)

fun PreguntaSesion.aRespuesta(mostrarCorreccion: Boolean = false) = RespuestaPreguntaEntrevista(
    preguntaSesionId = id.toString(),
    orden = orden,
    enunciado = enunciado,
    tipo = tipo.valorBd,
    categoria = categoria.valorBd,
    opciones = opciones.map { RespuestaOpcionEntrevista(it.id, it.texto) },
    respondida = estaRespondida,
    respuesta = fechaRespuesta?.let { fecha -> RespuestaDadaEntrevista(transcripcion, opcionElegidaId, videoClipUrl, fecha.toString()) },
    correccion = if (mostrarCorreccion) aCorreccion() else null
)

private fun PreguntaSesion.aCorreccion() = CorreccionPreguntaEntrevista(
    opcionCorrectaId = opcionCorrecta?.id,
    esCorrecta = if (tipo == TipoPregunta.OPCION_MULTIPLE && estaRespondida) opcionElegidaId == opcionCorrecta?.id else null,
    puntaje = puntaje?.toDouble(),
    respuestaIdeal = respuestaIdeal
)

fun ResumenSesionEntrevista.aRespuesta() = RespuestaSesionEntrevista(
    sesionId = id.toString(),
    cargoId = cargoId?.toString(),
    cargo = cargoObjetivo,
    nivel = nivel.valorBd,
    estado = estado.valorBd,
    fechaInicio = fechaInicio.toString(),
    fechaFin = fechaFin?.toString(),
    totalPreguntas = totalPreguntas,
    respondidas = respondidas
)

fun PaginaSesionesEntrevista.aRespuesta() = RespuestaHistorialEntrevistas(elementos.map { it.aRespuesta() }, total, pagina, tamano)

// ---------- Contrato de la app Android (/api/prueba-practica, tipo ENT) ----------

const val TIPO_PRUEBA_ENTREVISTA = "ENT"
private const val TIPO_BANCO_TECNICO = "PR"
private const val TIPO_BANCO_BLANDO = "BL"
private const val TIPO_ABIERTA_APP = "abierta"
private const val MINIMO_CARACTERES_ABIERTA = 1

fun SesionEntrevista.aPruebaPractica(sector: String) = RespuestaCrearPruebaPractica(
    pruebaId = id.toString(),
    tipoPrueba = TIPO_PRUEBA_ENTREVISTA,
    area = sector,
    nivel = nivel.codigoApp,
    metadata = buildMap {
        put("metaCargo", cargoObjetivo)
        put("nivelSolicitado", nivel.codigoApp)
        cargoId?.let { put("cargoId", it.toString()) }
    },
    preguntas = preguntas.map { it.aPreguntaPractica(sector, nivel.codigoApp) }
)

private fun PreguntaSesion.aPreguntaPractica(sector: String, nivelApp: String): PreguntaPruebaPractica {
    val esOpcionMultiple = tipo == TipoPregunta.OPCION_MULTIPLE
    // La app no graba video: las preguntas de simulación en video se responden por escrito.
    val tipoApp = if (esOpcionMultiple) TipoPregunta.OPCION_MULTIPLE.valorBd else TIPO_ABIERTA_APP
    return PreguntaPruebaPractica(
        preguntaId = id.toString(),
        texto = enunciado,
        tipoBanco = if (categoria == CategoriaHabilidad.BLANDA) TIPO_BANCO_BLANDO else TIPO_BANCO_TECNICO,
        sector = sector,
        nivel = nivelApp,
        tipoPregunta = tipoApp,
        configRespuesta = if (esOpcionMultiple) {
            ConfigRespuestaPruebaPractica(opciones = opciones.map { OpcionPruebaPractica(it.id, it.texto) }, tipo = tipoApp)
        } else {
            ConfigRespuestaPruebaPractica(
                minCaracteres = MINIMO_CARACTERES_ABIERTA,
                maxCaracteres = LARGO_MAXIMO_RESPUESTA_ENTREVISTA,
                // Las preguntas de comportamiento se responden con el método STAR.
                formato = if (categoria == CategoriaHabilidad.BLANDA) "STAR" else null,
                tipo = tipoApp
            )
        },
        orden = orden
    )
}

/** Resultado inmediato: corrige la opción múltiple; las abiertas quedan para el reporte de feedback. */
fun SesionEntrevista.aResultadoPractica(): RespuestaEnviarRespuestasPractica {
    val corregibles = preguntas.filter { it.tipo == TipoPregunta.OPCION_MULTIPLE }
    val detalle = corregibles.map { pregunta ->
        ResultadoPreguntaPractica(
            preguntaId = pregunta.id.toString(),
            correcta = pregunta.estaRespondida && pregunta.opcionElegidaId == pregunta.opcionCorrecta?.id,
            claveCorrecta = pregunta.opcionCorrecta?.id,
            seleccionadas = listOfNotNull(pregunta.opcionElegidaId)
        )
    }
    val correctas = detalle.count { it.correcta }
    val abiertasRespondidas = preguntas.count { it.tipo != TipoPregunta.OPCION_MULTIPLE && it.estaRespondida }
    return RespuestaEnviarRespuestasPractica(
        ok = true,
        puntaje = correctas,
        totalPreguntas = corregibles.size,
        respondidas = respondidas,
        correctas = correctas,
        detalle = detalle,
        feedbackGeneral = when {
            abiertasRespondidas == 0 -> "Respondiste correctamente $correctas de ${corregibles.size} preguntas de alternativas."
            else -> "Respondiste correctamente $correctas de ${corregibles.size} preguntas de alternativas. " +
                "Tus $abiertasRespondidas respuestas abiertas quedaron guardadas para el reporte de feedback."
        }
    )
}
