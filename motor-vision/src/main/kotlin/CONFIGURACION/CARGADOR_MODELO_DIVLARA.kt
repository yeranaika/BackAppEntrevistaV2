package CONFIGURACION

import ERRORES.ErrorServicioExterno
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant

/** Carpeta raíz dentro del backend donde se empaquetan los modelos de visión. */
const val DIRECTORIO_MODELOS_DIVLARA = "models"

/** Nombre esperado del artefacto binario dentro de `models/<version>/`. */
const val ARTEFACTO_MODELO_DIVLARA = "modelo.onnx"

/** Parámetros del `StandardScaler` (`mean` y `scale`) exportados desde DivLara. */
const val ARCHIVO_SCALER_DIVLARA = "scaler.json"

/** Metadatos del modelo (`feature_columns` y `targets`) exportados desde el registro de DivLara. */
const val ARCHIVO_META_DIVLARA = "meta.json"

private val JSON_MODELO_DIVLARA = Json { ignoreUnknownKeys = true }

/** Parámetros de normalización `(x - mean) / scale` equivalentes a `StandardScaler` de scikit-learn. */
class ScalerModeloDivlara(
    val mean: DoubleArray,
    val scale: DoubleArray
) {
    init {
        require(mean.isNotEmpty()) { "El scaler del modelo no puede estar vacío" }
        require(mean.size == scale.size) {
            "El scaler tiene longitudes incompatibles: mean=${mean.size}, scale=${scale.size}"
        }
        require(mean.all { it.isFinite() }) { "El scaler contiene medias no finitas" }
        require(scale.all { it.isFinite() && it != 0.0 }) { "El scaler contiene desviaciones inválidas o en cero" }
    }

    val cantidadFeatures: Int get() = mean.size

    /** Normaliza en doble precisión antes de pasar a float32 para evitar cancelación numérica en escalas pequeñas. */
    fun normalizar(features: DoubleArray): FloatArray {
        require(features.size == mean.size) {
            "Cantidad de features (${features.size}) distinta al scaler (${mean.size})"
        }
        return FloatArray(mean.size) { indice ->
            ((features[indice] - mean[indice]) / scale[indice]).toFloat()
        }
    }
}

/** Columnas de entrada y variables de salida leídas desde `meta.json`. */
data class MetadatosModeloDivlara(
    val columnasEntrada: List<String>,
    val variablesSalida: List<String>
) {
    init {
        require(columnasEntrada.isNotEmpty()) { "meta.json no define feature_columns" }
        require(variablesSalida.isNotEmpty()) { "meta.json no define targets" }
    }
}

/**
 * Instancia cargada del artefacto DivLara. Envuelve la sesión de ONNX Runtime
 * y libera memoria nativa al cerrarse con el servidor.
 */
interface ArtefactoModeloDivlara : AutoCloseable {
    val ruta: Path
    val tamanoBytes: Long
    val cantidadFeatures: Int get() = 0
    val variablesSalida: List<String> get() = emptyList()
    val mediasScaler: DoubleArray? get() = null
    fun predecir(features: DoubleArray): Map<String, Double> {
        throw UnsupportedOperationException("El artefacto cargado no soporta inferencia")
    }
    override fun close() {}
}

/** Motor desacoplado que lee/inicializa el artefacto desde disco. */
fun interface LectorArtefactoModeloDivlara {
    fun cargar(archivo: Path): ArtefactoModeloDivlara
}

@Serializable
private data class ScalerJsonArchivo(
    val mean: List<Double>,
    val scale: List<Double>
)

@Serializable
private data class MetaJsonArchivo(
    @SerialName("feature_columns")
    val featureColumns: List<String>,
    val targets: List<String>
)

private data class ArtefactoArchivoPreparado(
    override val ruta: Path,
    override val tamanoBytes: Long
) : ArtefactoModeloDivlara

/**
 * Lector liviano de verificación de archivo (sin sesión ONNX); útil en pruebas unitarias de ciclo de vida.
 */
class LectorArtefactoArchivoPreparado : LectorArtefactoModeloDivlara {
    override fun cargar(archivo: Path): ArtefactoModeloDivlara {
        val tamano = validarArchivoLegible(archivo)
        return ArtefactoArchivoPreparado(ruta = archivo, tamanoBytes = tamano)
    }
}

/** Sesión ONNX Runtime lista para inferencia concurrente, junto con su scaler y metadatos. */
class ArtefactoOnnxDivlara(
    override val ruta: Path,
    override val tamanoBytes: Long,
    private val entorno: OrtEnvironment,
    private val sesion: OrtSession,
    private val nombreEntrada: String,
    private val scaler: ScalerModeloDivlara,
    private val metadatos: MetadatosModeloDivlara
) : ArtefactoModeloDivlara {

    override val cantidadFeatures: Int = scaler.cantidadFeatures
    override val variablesSalida: List<String> = metadatos.variablesSalida
    override val mediasScaler: DoubleArray get() = scaler.mean.copyOf()

    override fun predecir(features: DoubleArray): Map<String, Double> {
        val entradaNormalizada = scaler.normalizar(features)
        val forma = longArrayOf(1L, entradaNormalizada.size.toLong())
        OnnxTensor.createTensor(entorno, FloatBuffer.wrap(entradaNormalizada), forma).use { tensor ->
            sesion.run(mapOf(nombreEntrada to tensor)).use { resultado ->
                val salida = extraerVectorSalida(resultado)
                if (salida.size != metadatos.variablesSalida.size) {
                    throw IllegalStateException(
                        "El modelo devolvió ${salida.size} salidas pero meta.json declara ${metadatos.variablesSalida.size}"
                    )
                }
                return buildMap(metadatos.variablesSalida.size) {
                    metadatos.variablesSalida.forEachIndexed { indice, variable ->
                        put(variable, salida[indice].toDouble())
                    }
                }
            }
        }
    }

    override fun close() {
        // Nunca cerrar OrtEnvironment.getEnvironment(): es singleton compartido del proceso.
        sesion.close()
    }

    private fun extraerVectorSalida(resultado: OrtSession.Result): FloatArray {
        val valor = resultado[0].value
        return when (valor) {
            is Array<*> -> (valor.firstOrNull() as? FloatArray)
                ?: throw IllegalStateException("La salida ONNX no tiene forma [1, N] de floats")
            is FloatArray -> valor
            else -> throw IllegalStateException("Tipo de salida ONNX no soportado: ${valor?.javaClass?.simpleName}")
        }
    }
}

/**
 * Lector ONNX Runtime: carga `modelo.onnx`, `scaler.json` y `meta.json` desde la carpeta del modelo
 * y valida consistencia entre dimensiones de entrada y variables de salida.
 */
class LectorModeloOnnxDivlara : LectorArtefactoModeloDivlara {
    override fun cargar(archivo: Path): ArtefactoModeloDivlara {
        val tamano = validarArchivoLegible(archivo)
        val carpeta = archivo.parent ?: throw IOException("No se pudo resolver la carpeta del modelo: $archivo")
        val rutaScaler = carpeta.resolve(ARCHIVO_SCALER_DIVLARA)
        val rutaMeta = carpeta.resolve(ARCHIVO_META_DIVLARA)
        validarArchivoLegible(rutaScaler)
        validarArchivoLegible(rutaMeta)

        val scalerJson = JSON_MODELO_DIVLARA.decodeFromString<ScalerJsonArchivo>(Files.readString(rutaScaler))
        val scaler = ScalerModeloDivlara(scalerJson.mean.toDoubleArray(), scalerJson.scale.toDoubleArray())

        val metaJson = JSON_MODELO_DIVLARA.decodeFromString<MetaJsonArchivo>(Files.readString(rutaMeta))
        val metadatos = MetadatosModeloDivlara(metaJson.featureColumns, metaJson.targets)

        if (metadatos.columnasEntrada.size != scaler.cantidadFeatures) {
            throw IOException(
                "Inconsistencia entre meta.json (${metadatos.columnasEntrada.size} features) " +
                    "y scaler.json (${scaler.cantidadFeatures} features)"
            )
        }

        val entorno = OrtEnvironment.getEnvironment()
        val opciones = OrtSession.SessionOptions()
        val sesion = try {
            opciones.use { entorno.createSession(archivo.toAbsolutePath().toString(), it) }
        } catch (error: OrtException) {
            throw IOException("No se pudo inicializar la sesión ONNX: ${error.message}", error)
        }

        try {
            val nombreEntrada = sesion.inputNames.firstOrNull()
                ?: throw IOException("El modelo ONNX no define entradas")
            validarDimensionesOnnx(sesion, nombreEntrada, scaler.cantidadFeatures, metadatos.variablesSalida.size)
            return ArtefactoOnnxDivlara(
                ruta = archivo,
                tamanoBytes = tamano,
                entorno = entorno,
                sesion = sesion,
                nombreEntrada = nombreEntrada,
                scaler = scaler,
                metadatos = metadatos
            )
        } catch (error: Exception) {
            runCatching { sesion.close() }
            throw error
        }
    }

    private fun validarDimensionesOnnx(
        sesion: OrtSession,
        nombreEntrada: String,
        esperadasEntrada: Int,
        esperadasSalida: Int
    ) {
        val infoEntrada = sesion.inputInfo[nombreEntrada]?.info as? TensorInfo
        val dimEntrada = infoEntrada?.shape?.getOrNull(1)?.toInt()
        if (dimEntrada != null && dimEntrada > 0 && dimEntrada != esperadasEntrada) {
            throw IOException("El modelo ONNX espera $dimEntrada features pero scaler/meta definen $esperadasEntrada")
        }
        val nombreSalida = sesion.outputNames.firstOrNull()
        val infoSalida = nombreSalida?.let { sesion.outputInfo[it]?.info as? TensorInfo }
        val dimSalida = infoSalida?.shape?.getOrNull(1)?.toInt()
        if (dimSalida != null && dimSalida > 0 && dimSalida != esperadasSalida) {
            throw IOException("El modelo ONNX devuelve $dimSalida salidas pero meta.json define $esperadasSalida targets")
        }
    }
}

private fun validarArchivoLegible(archivo: Path): Long {
    if (!Files.isRegularFile(archivo)) {
        throw IOException("La ruta del modelo no es un archivo regular: ${archivo.fileName}")
    }
    if (!Files.isReadable(archivo)) {
        throw IOException("El artefacto del modelo no tiene permisos de lectura: ${archivo.fileName}")
    }
    val tamano = Files.size(archivo)
    if (tamano <= 0L) {
        throw IOException("El artefacto del modelo está vacío: ${archivo.fileName}")
    }
    Files.newInputStream(archivo).use { entrada ->
        if (entrada.read() == -1) {
            throw IOException("No se pudieron leer bytes del artefacto: ${archivo.fileName}")
        }
    }
    return tamano
}

/**
 * Carga una sola vez al arranque el paquete del modelo DivLara desde `models/<version>/`
 * (`modelo.onnx`, `scaler.json` y `meta.json`).
 * Si falta alguno de los archivos, queda en [EstadoCargaModeloDivlara.NO_DISPONIBLE] sin impedir el arranque ni fallar `/health`.
 * Si los archivos existen pero falla la carga, registra el error y deja el servicio vivo.
 */
class CargadorModeloDivlara(
    val version: String = VERSION_MODELO_DIVLARA,
    directorioBase: Path = Path.of(DIRECTORIO_MODELOS_DIVLARA),
    nombreArtefacto: String = ARTEFACTO_MODELO_DIVLARA,
    private val lector: LectorArtefactoModeloDivlara = LectorModeloOnnxDivlara(),
    private val reloj: Clock = Clock.systemUTC()
) : ProveedorEstadoModeloDivlara, MotorInferenciaDivlara, AutoCloseable {

    private val log = LoggerFactory.getLogger(CargadorModeloDivlara::class.java)

    val directorioModelo: Path = directorioBase.resolve(version)
    val rutaArtefacto: Path = directorioModelo.resolve(nombreArtefacto)
    val rutaScaler: Path = directorioModelo.resolve(ARCHIVO_SCALER_DIVLARA)
    val rutaMeta: Path = directorioModelo.resolve(ARCHIVO_META_DIVLARA)

    private val bloqueo = Any()
    private var inicializado = false
    private var artefactoCargado: ArtefactoModeloDivlara? = null

    @Volatile
    private var estadoActual: EstadoModeloDivlara = EstadoModeloDivlara.noDisponible(version)

    /**
     * Intenta cargar el modelo si todavía no se ejecutó la carga. Es idempotente: llamadas posteriores
     * devuelven el estado ya resuelto sin volver a leer los archivos.
     */
    fun cargarSiDisponible(): EstadoModeloDivlara {
        synchronized(bloqueo) {
            if (inicializado) return estadoActual
            inicializado = true

            val faltantes = listOf(rutaArtefacto, rutaScaler, rutaMeta).filterNot { Files.exists(it) }
            if (faltantes.isNotEmpty()) {
                log.info(
                    "Modelo DivLara ({}) no disponible en {} (faltan: {}): estado NO_DISPONIBLE; el backend arranca normalmente.",
                    version,
                    directorioModelo,
                    faltantes.joinToString(", ") { it.fileName.toString() }
                )
                estadoActual = EstadoModeloDivlara.noDisponible(version)
                return estadoActual
            }

            try {
                val artefacto = lector.cargar(rutaArtefacto)
                artefactoCargado = artefacto
                estadoActual = EstadoModeloDivlara(
                    version = version,
                    estado = EstadoCargaModeloDivlara.CARGADO,
                    cargado = true,
                    tamanoBytes = artefacto.tamanoBytes,
                    fechaCarga = reloj.instant()
                )
                log.info(
                    "Modelo DivLara ({}) cargado desde {} ({} bytes, {} features, {} variables).",
                    version,
                    rutaArtefacto,
                    artefacto.tamanoBytes,
                    artefacto.cantidadFeatures,
                    artefacto.variablesSalida.size
                )
            } catch (error: Exception) {
                val detalle = error.message?.takeIf { it.isNotBlank() }
                    ?: error::class.simpleName
                    ?: "error_carga_modelo"
                val tamano = runCatching {
                    if (Files.isRegularFile(rutaArtefacto)) Files.size(rutaArtefacto) else null
                }.getOrNull()
                estadoActual = EstadoModeloDivlara(
                    version = version,
                    estado = EstadoCargaModeloDivlara.ERROR,
                    cargado = false,
                    tamanoBytes = tamano,
                    error = detalle
                )
                log.warn(
                    "No se pudo cargar el modelo DivLara ({}) desde {}: {}",
                    version,
                    rutaArtefacto,
                    detalle,
                    error
                )
            }
            return estadoActual
        }
    }

    override fun estado(): EstadoModeloDivlara = estadoActual

    /** Artefacto en memoria si la carga fue exitosa; `null` si está no disponible o con error. */
    fun artefacto(): ArtefactoModeloDivlara? = synchronized(bloqueo) { artefactoCargado }

    override fun cantidadFeaturesEsperadas(): Int? = artefacto()?.cantidadFeatures?.takeIf { it > 0 }

    override fun mediasScaler(): DoubleArray? = artefacto()?.mediasScaler

    override fun predecir(features: List<Double>): ResultadoPrediccionDivlara {
        val actual = artefacto() ?: throw ErrorServicioExterno(
            "modelo_no_disponible",
            "El modelo de visión ($version) no está disponible en este momento"
        )
        val predicciones = try {
            actual.predecir(features.toDoubleArray())
        } catch (error: OrtException) {
            log.warn("Error de ONNX Runtime al inferir con modelo DivLara ({}): {}", version, error.message, error)
            throw ErrorServicioExterno(
                "error_inferencia_modelo",
                "No se pudo ejecutar la inferencia del modelo de visión",
                error
            )
        }
        return ResultadoPrediccionDivlara(
            versionModelo = version,
            predicciones = predicciones,
            escala = ESCALA_MODELO_DIVLARA
        )
    }

    override fun close() {
        synchronized(bloqueo) {
            val actual = artefactoCargado ?: return
            artefactoCargado = null
            runCatching { actual.close() }.onFailure {
                log.warn("Error al cerrar artefacto del modelo DivLara ({}): {}", version, it.message, it)
            }
        }
    }
}
