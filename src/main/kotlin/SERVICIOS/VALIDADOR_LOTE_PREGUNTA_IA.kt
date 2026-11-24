package SERVICIOS

import ESQUEMAS.CRITERIOS_MINIMOS_RUBRICA
import ESQUEMAS.LoteLlm
import ESQUEMAS.OPCIONES_POR_PREGUNTA_IA
import ESQUEMAS.PALABRAS_CLAVE_MINIMAS
import ESQUEMAS.PreguntaLlm
import MODELOS.CategoriaHabilidad
import MODELOS.TipoPregunta
import kotlinx.serialization.json.Json

private const val LARGO_MINIMO_RESPUESTA_IDEAL = 40
private const val ORACIONES_MINIMAS_RESPUESTA = 3
private const val ORACIONES_MINIMAS_RESPUESTA_TECNICA = 2
private const val LARGO_MINIMO_ORACION = 12
/** Admite términos cortos de tecnologías: "Go", "C#", "R" con 2 letras o símbolo ("C#" mide 2). */
private const val LARGO_MINIMO_PALABRA_CLAVE = 2

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

    /** Método de la rúbrica que se pide a la IA según la categoría: STAR solo para las de comportamiento. */
    fun metodoDe(categoria: CategoriaHabilidad) = if (categoria == CategoriaHabilidad.BLANDA) "STAR" else "TECNICA"

    fun parsearYValidar(
        json: String,
        cantidadEsperada: Int,
        tipoEsperado: TipoPregunta,
        categoria: CategoriaHabilidad = CategoriaHabilidad.BLANDA
    ): List<PreguntaLlm> {
        val preguntas = try {
            jsonEstricto.decodeFromString<LoteLlm>(json).preguntas
        } catch (e: Exception) {
            throw LoteInvalidoException("json_invalido: ${e.javaClass.simpleName}")
        }
        exigir(preguntas.size == cantidadEsperada, "cantidad_incorrecta")
        exigir(preguntas.map { identidad(it.enunciado) }.toSet().size == preguntas.size, "enunciado_duplicado")
        // Encabezados fuera y "Situación:" → "S:" antes de validar: así se guarda en el formato de la app (D40).
        val normalizadas = preguntas.map { it.copy(respuestaIdeal = FormatoRespuestaIdeal.normalizar(it.respuestaIdeal)) }
        normalizadas.forEach { validarPregunta(it, tipoEsperado, categoria) }
        return normalizadas
    }

    private fun validarPregunta(pregunta: PreguntaLlm, tipoEsperado: TipoPregunta, categoria: CategoriaHabilidad) {
        val esDeComportamiento = categoria == CategoriaHabilidad.BLANDA
        exigir(pregunta.enunciado.isNotBlank(), "enunciado_vacio")
        exigir(pregunta.tipoPregunta == tipoEsperado.valorBd, "tipo_incorrecto")
        exigir(pregunta.respuestaIdeal.length >= LARGO_MINIMO_RESPUESTA_IDEAL, "respuesta_incompleta")

        val rubrica = pregunta.rubrica
        exigir(rubrica.criterios.size >= CRITERIOS_MINIMOS_RUBRICA, "faltan_criterios")
        if (esDeComportamiento) {
            // Solo las de comportamiento se responden con STAR; una pregunta técnica forzada a ese formato sale rara.
            exigir(pregunta.respuestaIdeal.let { segmentosTipoOracion(it) >= ORACIONES_MINIMAS_RESPUESTA }, "respuesta_sin_tres_oraciones")
            exigir(rubrica.metodo == "STAR", "metodo_incorrecto")
            val criterios = normalizar(rubrica.criterios.joinToString(" "))
            val respuesta = normalizar(pregunta.respuestaIdeal)
            val conMarcas = FormatoRespuestaIdeal.tieneStar(pregunta.respuestaIdeal)
            componentesStar.forEach { componente ->
                exigir(criterios.contains(componente), "rubrica_sin_$componente")
                // Con las marcas S:, T:, A: y R: la parte ya está nombrada; sin ellas tiene que aparecer la palabra.
                exigir(conMarcas || respuesta.contains(componente), "respuesta_sin_$componente")
            }
            // En las abiertas y de video la app ordena la respuesta por esas marcas: sin ellas se vería como un párrafo.
            if (tipoEsperado != TipoPregunta.OPCION_MULTIPLE) exigir(conMarcas, "respuesta_sin_formato_star")
        } else {
            exigir(segmentosTipoOracion(pregunta.respuestaIdeal) >= ORACIONES_MINIMAS_RESPUESTA_TECNICA, "respuesta_sin_dos_oraciones")
            exigir(rubrica.metodo == metodoDe(categoria) || rubrica.metodo == "STAR", "metodo_incorrecto")
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
        .split(Regex("[.!?]+|(?<![\\p{L}\\p{N}])(?=[STAR]:\\s)|(?<![\\p{L}\\p{N}])(?=\\d{1,2}\\)\\s)|(?i)(?=\\b(?:situación|tarea|acción|resultado)\\s*:)"))
        .count { it.trim().length >= LARGO_MINIMO_ORACION }

    private fun identidad(texto: String): String = normalizar(texto).replace(Regex("\\s+"), " ").trim()

    private fun normalizar(texto: String): String = texto.lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
}
