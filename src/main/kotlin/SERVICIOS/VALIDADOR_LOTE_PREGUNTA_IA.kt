package SERVICIOS

import ESQUEMAS.CRITERIOS_MINIMOS_RUBRICA
import ESQUEMAS.LoteLlm
import ESQUEMAS.OPCIONES_POR_PREGUNTA_IA
import ESQUEMAS.PALABRAS_CLAVE_MINIMAS
import ESQUEMAS.PreguntaLlm
import MODELOS.TipoPregunta
import kotlinx.serialization.json.Json

private const val LARGO_MINIMO_RESPUESTA_IDEAL = 40
private const val ORACIONES_MINIMAS_RESPUESTA = 3
private const val LARGO_MINIMO_ORACION = 12
private const val LARGO_MINIMO_PALABRA_CLAVE = 3

/** El lote del LLM no cumple las reglas; el motivo queda para el log, nunca para el cliente. */
class LoteInvalidoException(motivo: String) : RuntimeException(motivo)

/**
 * Valida el JSON del LLM aunque se haya pedido con schema estricto: los proveedores no siempre lo respetan
 * y una pregunta mal formada no debe llegar al banco.
 */
object ValidadorLotePreguntaIa {
    private val jsonEstricto = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = true
    }
    private val componentesStar = listOf("situacion", "tarea", "accion", "resultado")

    fun parsearYValidar(json: String, cantidadEsperada: Int, tipoEsperado: TipoPregunta): List<PreguntaLlm> {
        val preguntas = try {
            jsonEstricto.decodeFromString<LoteLlm>(json).preguntas
        } catch (e: Exception) {
            throw LoteInvalidoException("json_invalido: ${e.javaClass.simpleName}")
        }
        exigir(preguntas.size == cantidadEsperada, "cantidad_incorrecta")
        exigir(preguntas.map { identidad(it.enunciado) }.toSet().size == preguntas.size, "enunciado_duplicado")
        preguntas.forEach { validarPregunta(it, tipoEsperado) }
        return preguntas
    }

    private fun validarPregunta(pregunta: PreguntaLlm, tipoEsperado: TipoPregunta) {
        exigir(pregunta.enunciado.isNotBlank(), "enunciado_vacio")
        exigir(pregunta.tipoPregunta == tipoEsperado.valorBd, "tipo_incorrecto")
        exigir(pregunta.respuestaIdeal.length >= LARGO_MINIMO_RESPUESTA_IDEAL, "respuesta_incompleta")
        exigir(segmentosTipoOracion(pregunta.respuestaIdeal) >= ORACIONES_MINIMAS_RESPUESTA, "respuesta_sin_tres_oraciones")

        val rubrica = pregunta.rubrica
        exigir(rubrica.metodo == "STAR", "metodo_incorrecto")
        exigir(rubrica.criterios.size >= CRITERIOS_MINIMOS_RUBRICA, "faltan_criterios")
        val criterios = normalizar(rubrica.criterios.joinToString(" "))
        val respuesta = normalizar(pregunta.respuestaIdeal)
        componentesStar.forEach { componente ->
            exigir(criterios.contains(componente), "rubrica_sin_$componente")
            exigir(respuesta.contains(componente), "respuesta_sin_$componente")
        }
        exigir(
            rubrica.palabrasClave.size >= PALABRAS_CLAVE_MINIMAS &&
                rubrica.palabrasClave.all { it.trim().length >= LARGO_MINIMO_PALABRA_CLAVE },
            "palabras_clave_invalidas"
        )
        exigir(rubrica.tiempoEsperadoSeg > 0, "tiempo_invalido")

        if (tipoEsperado == TipoPregunta.OPCION_MULTIPLE) validarOpciones(pregunta) else exigir(pregunta.opciones == null, "opciones_no_permitidas")
    }

    private fun validarOpciones(pregunta: PreguntaLlm) {
        val opciones = pregunta.opciones ?: throw LoteInvalidoException("faltan_opciones")
        exigir(opciones.size == OPCIONES_POR_PREGUNTA_IA, "cantidad_opciones_incorrecta")
        exigir(opciones.count { it.esCorrecta } == 1, "debe_haber_una_correcta")
        exigir(opciones.all { it.texto.isNotBlank() && it.explicacion.isNotBlank() }, "opcion_vacia")
        exigir(opciones.map { identidad(it.texto) }.toSet().size == opciones.size, "opcion_duplicada")
    }

    private fun exigir(condicion: Boolean, motivo: String) {
        if (!condicion) throw LoteInvalidoException(motivo)
    }

    private fun segmentosTipoOracion(texto: String): Int = texto
        .split(Regex("[.!?]+|(?i)(?=\\b(?:situación|tarea|acción|resultado)\\s*:)"))
        .count { it.trim().length >= LARGO_MINIMO_ORACION }

    private fun identidad(texto: String): String = normalizar(texto).replace(Regex("\\s+"), " ").trim()

    private fun normalizar(texto: String): String = texto.lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
}
