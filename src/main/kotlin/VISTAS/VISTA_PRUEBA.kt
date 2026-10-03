package VISTAS

import CONFIGURACION.LARGO_MAXIMO_RESPUESTA_ENTREVISTA
import ESQUEMAS.ConfigRespuestaPruebaPractica
import ESQUEMAS.IntentoPruebaApp
import ESQUEMAS.OpcionPruebaPractica
import ESQUEMAS.PreguntaPruebaPractica
import ESQUEMAS.RespuestaCorreccion
import ESQUEMAS.RespuestaCrearPruebaPractica
import ESQUEMAS.RespuestaEnviarRespuestasPractica
import ESQUEMAS.RespuestaEvaluacionSkill
import ESQUEMAS.RespuestaIntentoNivelacion
import ESQUEMAS.RespuestaNivelSkill
import ESQUEMAS.RespuestaOpcionPrueba
import ESQUEMAS.RespuestaPreguntaServida
import ESQUEMAS.RespuestaResultadoNivelacion
import ESQUEMAS.RespuestaResumenPrueba
import ESQUEMAS.RespuestaSesionPracticaApi
import ESQUEMAS.RespuestaTestNivelacion
import ESQUEMAS.ResultadoPreguntaPractica
import MODELOS.CategoriaHabilidad
import MODELOS.EvaluacionSkill
import MODELOS.IntentoNivelacion
import MODELOS.NivelExperiencia
import MODELOS.NivelSkillUsuario
import MODELOS.OpcionSnapshot
import MODELOS.PreguntaServida
import MODELOS.RespuestaCorregida
import MODELOS.RespuestaPractica
import MODELOS.ResultadoNivelacion
import MODELOS.ResumenPrueba
import MODELOS.SesionPractica
import MODELOS.TestNivelacion
import MODELOS.TipoPregunta
import SERVICIOS.nombreNivel

// ---------- API /api/v1 ----------

private fun PreguntaServida.aRespuesta(correccion: RespuestaCorreccion?) = RespuestaPreguntaServida(
    preguntaId = id,
    orden = orden,
    enunciado = enunciado,
    tipo = tipo,
    categoria = categoria,
    nivel = nivel,
    opciones = opciones.map { RespuestaOpcionPrueba(it.id, it.texto) },
    respondida = correccion != null,
    correccion = correccion
)

/** Al responder se muestra la corrección completa: la opción correcta y la respuesta ideal. */
private fun PreguntaServida.correccion(opcionId: String?, texto: String?, correcta: Boolean, puntaje: Double, feedback: String?) =
    RespuestaCorreccion(
        opcionId = opcionId,
        texto = texto,
        correcta = correcta,
        puntaje = puntaje,
        feedback = feedback,
        opcionCorrectaId = opcionCorrecta?.id,
        respuestaIdeal = respuestaIdeal
    )

fun RespuestaPractica.aCorreccion(pregunta: PreguntaServida) =
    pregunta.correccion(opcionElegidaId, texto, esCorrecta, puntaje.toDouble(), feedback)

private fun RespuestaCorregida.aCorreccion(pregunta: PreguntaServida) = pregunta.correccion(opcionId, texto, correcta, puntaje, feedback)

fun SesionPractica.aRespuesta() = RespuestaSesionPracticaApi(
    sesionId = id.toString(),
    skillId = skillId?.toString(),
    cargoId = cargoId?.toString(),
    cargo = cargoObjetivo,
    modo = modo.valorBd,
    categoria = categoria.valorBd,
    nivel = nivel.valorBd,
    estado = estado.valorBd,
    puntaje = puntaje?.toDouble(),
    totalPreguntas = preguntas.size,
    respondidas = respuestas.size,
    correctas = correctas,
    fechaInicio = fechaInicio.toString(),
    fechaFin = fechaFin?.toString(),
    preguntas = preguntas.map { pregunta -> pregunta.aRespuesta(respuestaDe(pregunta.id)?.aCorreccion(pregunta)) }
)

/** La corrección se ve recién cuando el intento terminó (para no revelar respuestas a mitad del test). */
fun IntentoNivelacion.aRespuesta(resultado: ResultadoNivelacion?) = RespuestaIntentoNivelacion(
    intentoId = id.toString(),
    cargoId = cargoId?.toString(),
    cargo = cargoObjetivo,
    estado = if (estaTerminado) "finalizada" else "en_progreso",
    fechaInicio = fechaInicio.toString(),
    fechaFin = fechaFin?.toString(),
    totalPreguntas = detalle.size,
    preguntas = detalle.map { item -> item.pregunta.aRespuesta(if (estaTerminado) item.respuesta?.aCorreccion(item.pregunta) else null) },
    resultado = resultado?.aRespuesta()
)

fun ResultadoNivelacion.aRespuesta() = RespuestaResultadoNivelacion(
    intentoId = intentoId.toString(),
    cargoId = cargoId?.toString(),
    nivel = nivelGlobal.valorBd,
    puntajeGlobal = puntajeGlobal.toDouble(),
    brechas = skillsBrecha.map { it.aRespuesta() },
    skillsOk = skillsOk.map { it.aRespuesta() },
    resumen = resumen,
    fecha = fecha.toString()
)

private fun EvaluacionSkill.aRespuesta() =
    RespuestaEvaluacionSkill(skillId, nombre, nivelActual, nivelRequerido, puntaje, brecha, prioridad)

fun ResumenPrueba.aRespuesta() = RespuestaResumenPrueba(
    id = id.toString(),
    tipo = tipo,
    cargo = cargoObjetivo,
    nivel = nivel?.valorBd,
    estado = estado,
    puntaje = puntaje,
    puntajeTotal = puntajeTotal,
    fechaInicio = fechaInicio.toString(),
    fechaFin = fechaFin?.toString()
)

fun TestNivelacion.aRespuesta() = RespuestaTestNivelacion(
    testId = id.toString(),
    titulo = titulo,
    cargoId = cargoId?.toString(),
    area = area,
    nivelObjetivo = nivelObjetivo,
    descripcion = descripcion,
    preguntasIds = preguntasIds.map { it.toString() },
    activo = estaActivo
)

fun NivelSkillUsuario.aRespuesta(nombre: String) =
    RespuestaNivelSkill(skillId.toString(), nombre, nivel?.valorBd, puntaje.toDouble(), evaluaciones, fecha.toString())

// ---------- Contrato de la app Android (/api/prueba-practica) ----------

const val TIPO_BANCO_TECNICO = "PR"
const val TIPO_BANCO_BLANDO = "BL"
private const val TIPO_BANCO_NIVELACION = "NV"
private const val TIPO_ABIERTA_APP = "abierta"
private const val MINIMO_CARACTERES_ABIERTA = 1
private const val MODO_FEEDBACK_NLP = "nlp"
private const val TIPO_PRUEBA_PRACTICA = "PR"
private const val TIPO_PRUEBA_NIVELACION = "NV"

/** Una pregunta como la dibuja la app: solo conoce opcion_multiple y abierta (no graba video). */
internal fun preguntaParaApp(
    id: String,
    texto: String,
    tipoBanco: String,
    sector: String,
    nivel: NivelExperiencia,
    tipo: TipoPregunta,
    categoria: CategoriaHabilidad,
    opciones: List<OpcionSnapshot>,
    orden: Int
): PreguntaPruebaPractica {
    val esOpcionMultiple = tipo == TipoPregunta.OPCION_MULTIPLE
    val tipoApp = if (esOpcionMultiple) TipoPregunta.OPCION_MULTIPLE.valorBd else TIPO_ABIERTA_APP
    return PreguntaPruebaPractica(
        preguntaId = id,
        texto = texto,
        tipoBanco = tipoBanco,
        sector = sector,
        nivel = nivel.codigoApp,
        tipoPregunta = tipoApp,
        // Nunca se envía cuál es la correcta.
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

private fun PreguntaServida.paraApp(sector: String, tipoBanco: String) = preguntaParaApp(
    id, enunciado, tipoBanco, sector, nivelExperiencia, tipoPregunta, categoriaHabilidad, opciones, orden
)

private fun bancoDe(categoria: CategoriaHabilidad) = if (categoria == CategoriaHabilidad.BLANDA) TIPO_BANCO_BLANDO else TIPO_BANCO_TECNICO

fun SesionPractica.aPruebaPractica(sector: String) = RespuestaCrearPruebaPractica(
    pruebaId = id.toString(),
    tipoPrueba = TIPO_PRUEBA_PRACTICA,
    area = sector,
    nivel = nivel.codigoApp,
    metadata = buildMap {
        cargoObjetivo?.let { put("metaCargo", it) }
        put("nivelSolicitado", nivel.codigoApp)
        put("tipoBanco", bancoDe(categoria))
        cargoId?.let { put("cargoId", it.toString()) }
    },
    preguntas = preguntas.map { it.paraApp(sector, bancoDe(categoria)) }
)

fun IntentoNivelacion.aPruebaPractica(sector: String) = RespuestaCrearPruebaPractica(
    pruebaId = id.toString(),
    tipoPrueba = TIPO_PRUEBA_NIVELACION,
    area = sector,
    // La nivelación recorre todos los niveles.
    nivel = null,
    metadata = buildMap {
        cargoObjetivo?.let { put("metaCargo", it) }
        cargoId?.let { put("cargoId", it.toString()) }
        put("tipoBanco", TIPO_BANCO_NIVELACION)
    },
    preguntas = detalle.map { it.pregunta.paraApp(sector, TIPO_BANCO_NIVELACION) }
)

/** Resultado de una práctica: todas las preguntas cuentan (las no respondidas como incorrectas). */
fun SesionPractica.aResultadoPractica(): RespuestaEnviarRespuestasPractica {
    val detalle = preguntas.map { pregunta ->
        val respuesta = respuestaDe(pregunta.id)
        ResultadoPreguntaPractica(pregunta.id, respuesta?.esCorrecta == true, pregunta.opcionCorrecta?.id, listOfNotNull(respuesta?.opcionElegidaId))
    }
    val abiertas = respuestas.count { r -> preguntas.firstOrNull { it.id == r.preguntaServidaId }?.tipoPregunta != TipoPregunta.OPCION_MULTIPLE }
    return RespuestaEnviarRespuestasPractica(
        ok = true,
        puntaje = correctas,
        totalPreguntas = preguntas.size,
        respondidas = respuestas.size,
        correctas = correctas,
        detalle = detalle,
        feedbackGeneral = "Respondiste bien $correctas de ${preguntas.size} preguntas (puntaje ${puntaje?.toInt() ?: 0}/100).",
        feedbackMode = if (abiertas > 0) MODO_FEEDBACK_NLP else null
    )
}

fun IntentoNivelacion.aResultadoPractica(resultado: ResultadoNivelacion): RespuestaEnviarRespuestasPractica {
    val detalleApp = detalle.map { item ->
        ResultadoPreguntaPractica(item.pregunta.id, item.respuesta?.correcta == true, item.pregunta.opcionCorrecta?.id, listOfNotNull(item.respuesta?.opcionId))
    }
    val correctas = detalleApp.count { it.correcta }
    val hayAbiertas = detalle.any { it.respuesta != null && it.pregunta.tipoPregunta != TipoPregunta.OPCION_MULTIPLE }
    return RespuestaEnviarRespuestasPractica(
        ok = true,
        puntaje = correctas,
        totalPreguntas = detalle.size,
        respondidas = detalle.count { it.respuesta != null },
        correctas = correctas,
        nivelDetectado = nombreNivel(resultado.nivelGlobal),
        detalle = detalleApp,
        feedbackGeneral = resultado.resumen,
        feedbackMode = if (hayAbiertas) MODO_FEEDBACK_NLP else null
    )
}

fun ResumenPrueba.aIntentoApp() = IntentoPruebaApp(
    intentoId = id.toString(),
    pruebaId = id.toString(),
    fechaInicio = fechaInicio.toString(),
    fechaFin = fechaFin?.toString(),
    puntaje = puntaje,
    puntajeTotal = puntajeTotal,
    nivel = nivel?.codigoApp,
    metaCargo = cargoObjetivo,
    tipoPrueba = tipo,
    estado = estado
)
