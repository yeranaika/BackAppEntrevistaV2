package VISTAS

import ESQUEMAS.RespuestaDiccionario
import ESQUEMAS.RespuestaListaDiccionarios
import ESQUEMAS.RespuestaListaTranscripciones
import ESQUEMAS.RespuestaResumenDiccionario
import ESQUEMAS.RespuestaTranscripcion
import MODELOS.DiccionarioTerminologia
import MODELOS.TranscripcionRespuesta

fun TranscripcionRespuesta.aRespuesta() = RespuestaTranscripcion(
    id = id.toString(), sesionId = sesionId.toString(), preguntaId = preguntaId.toString(), inicioMs = inicioMs, finMs = finMs,
    texto = texto, idioma = idioma, confianza = confianza, parcial = parcial
)

fun List<TranscripcionRespuesta>.aRespuesta() = RespuestaListaTranscripciones(map { it.aRespuesta() })

fun DiccionarioTerminologia.aResumen() = RespuestaResumenDiccionario(
    area = area, nombre = nombre, version = version, activo = activo,
    totalTerminos = terminos.size, totalCorrecciones = correcciones.size
)

fun List<DiccionarioTerminologia>.aRespuestaLista() = RespuestaListaDiccionarios(map { it.aResumen() })

fun DiccionarioTerminologia.aRespuesta() = RespuestaDiccionario(
    area = area, nombre = nombre, version = version, activo = activo, terminos = terminos, correcciones = correcciones
)
