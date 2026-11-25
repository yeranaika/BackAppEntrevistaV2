package CONFIGURACION

import java.time.Instant

enum class EstadoCargaModeloDivlara {
    NO_DISPONIBLE,
    CARGADO,
    ERROR
}

/** Estado observable del modelo DivLara (en el motor: tras cargarlo al arrancar; en el backend: último estado conocido del motor). */
data class EstadoModeloDivlara(
    val version: String,
    val estado: EstadoCargaModeloDivlara,
    val cargado: Boolean,
    val tamanoBytes: Long? = null,
    val fechaCarga: Instant? = null,
    val error: String? = null,
    val ep: String? = null
) {
    companion object {
        fun noDisponible(version: String = VERSION_MODELO_DIVLARA) = EstadoModeloDivlara(
            version = version,
            estado = EstadoCargaModeloDivlara.NO_DISPONIBLE,
            cargado = false
        )
    }
}

/** Resultado de una inferencia ejecutada por el modelo DivLara (escala `0-8`), con desagregación opcional de mano-cara. */
data class ResultadoPrediccionDivlara(
    val versionModelo: String,
    val predicciones: Map<String, Double>,
    val escala: String = ESCALA_MODELO_DIVLARA,
    val manoCaraUna: Double? = null,
    val manoCaraDos: Double? = null,
    val manoCaraConteo: Int? = null
)

/** Contrato desacoplado de inferencia sobre las 343 features (lo implementa el motor con ONNX Runtime). */
interface MotorInferenciaDivlara {
    fun cantidadFeaturesEsperadas(): Int?
    /** Medias del `StandardScaler` (`scaler.json`) usadas como fallback cuando faltan landmarks (ej. iris en pose33). */
    fun mediasScaler(): DoubleArray? = null
    fun predecir(features: List<Double>): ResultadoPrediccionDivlara
}

/** Contrato mínimo de solo lectura para consultar el estado del modelo desde `/health` u otros módulos. */
fun interface ProveedorEstadoModeloDivlara {
    fun estado(): EstadoModeloDivlara
}
