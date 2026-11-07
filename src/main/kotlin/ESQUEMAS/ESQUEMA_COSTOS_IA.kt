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
    val porModelo: List<RespuestaCostoIaModelo>
)
