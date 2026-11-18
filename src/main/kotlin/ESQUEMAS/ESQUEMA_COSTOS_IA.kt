package ESQUEMAS

import kotlinx.serialization.Serializable

// Todos los campos sin valor por defecto: el panel los necesita siempre (encodeDefaults = false).

/** Gasto en IA de un grupo de usos. Los costos van en USD con 6 decimales. */
@Serializable
data class RespuestaGastoIa(
    /** Usos exitosos: reportes evaluados con IA o preguntas generadas. */
    val usos: Int,
    /** Llamadas que fallaron (respuesta inválida): se pagaron igual. */
    val fallidas: Int,
    val tokensEntrada: Long,
    val tokensSalida: Long,
    val costoUsd: Double,
    /** Parte de costoUsd estimada con la tabla de precios porque la llamada falló y no guardó su costo. */
    val costoEstimadoUsd: Double
)

@Serializable
data class RespuestaCostoIaDia(
    /** Día en UTC (AAAA-MM-DD). */
    val fecha: String,
    val reportes: RespuestaGastoIa,
    val generacionPreguntas: RespuestaGastoIa,
    val costoUsd: Double
)

@Serializable
data class RespuestaCostoIaModelo(
    val modelo: String,
    val gasto: RespuestaGastoIa
)

/**
 * Gasto del mes UTC en curso frente al tope de la generación de preguntas con IA (D23, D28). Es lo mismo que revisa el
 * servidor antes de cada generación: la próxima responde 429 tope_generacion_ia_alcanzado si estaAgotado o
 * estaDesactivado es true. El panel debe usar esos booleanos y no recalcularlos con los montos redondeados.
 */
@Serializable
data class RespuestaTopeGeneracionIa(
    /** Mes calendario UTC en curso (AAAA-MM); no depende de desde ni de hasta. */
    val mes: String,
    /** Lotes y llamadas fallidas (con su costo estimado) del mes, en USD con 6 decimales. */
    val gastadoUsd: Double,
    /** LIMITE_GASTO_GENERACION_IA_MENSUAL_USD tal cual (sin redondear). */
    val topeUsd: Double,
    /** max(0, tope − gastado), 6 decimales; 0 si está desactivado. */
    val restanteUsd: Double,
    /** Día 1 del mes siguiente (AAAA-MM-DD, UTC); null si está desactivado. Viaja siempre, también en null. */
    val fechaReposicion: String?,
    /** Tope 0 (o un valor ilegible en la variable): la generación con IA está apagada. */
    val estaDesactivado: Boolean,
    /** Gastado ≥ tope (sin redondear): la próxima generación responde 429. Nunca true junto a estaDesactivado. */
    val estaAgotado: Boolean
)

@Serializable
data class RespuestaCostosIa(
    /** Primer día incluido (UTC). */
    val desde: String,
    /** Último día incluido (UTC). */
    val hasta: String,
    val total: RespuestaGastoIa,
    val reportes: RespuestaGastoIa,
    val generacionPreguntas: RespuestaGastoIa,
    /** Solo los días con gasto, del más antiguo al más reciente. */
    val porDia: List<RespuestaCostoIaDia>,
    /** De mayor a menor costo. */
    val porModelo: List<RespuestaCostoIaModelo>,
    val topeGeneracionPreguntas: RespuestaTopeGeneracionIa
)
