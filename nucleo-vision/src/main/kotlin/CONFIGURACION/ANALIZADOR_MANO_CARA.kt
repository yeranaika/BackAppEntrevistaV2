package CONFIGURACION

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.hypot

/**
 * Desagregación geométrica de contacto mano-cara sobre una ventana P-1 de landmarks MediaPipe Pose.
 *
 * Complementa la predicción agregada `hand_to_face_adaptors` del modelo ONNX sin alterar el mapa
 * `predicciones` (contrato Android): distingue si en la ventana el contacto proviene de una sola mano
 * o de ambas manos simultáneamente.
 */
data class DetalleManoCara(
    val manoCaraUna: Double,
    val manoCaraDos: Double,
    val manoCaraConteo: Int
)

private const val LM_CARA_MIN = 0
private const val LM_CARA_MAX = 10
private const val LM_OREJA_IZQ = 7
private const val LM_OREJA_DER = 8
private const val LM_MUNECA_IZQ = 15
private const val LM_MUNECA_DER = 16
private val LMS_DEDOS_IZQ = intArrayOf(17, 19, 21)
private val LMS_DEDOS_DER = intArrayOf(18, 20, 22)

/** Visibilidad mínima para considerar fiable un landmark de cara o mano (alineado con el overlay Android). */
private const val VISIBILIDAD_MINIMA_MANO_CARA = 0.5

/** Mínimo de landmarks faciales visibles (0..10) para estimar centro y ancho del rostro. */
private const val MIN_LANDMARKS_CARA = 4

/** Multiplicador sobre el ancho del rostro (oreja-oreja) para la cercanía de muñeca (15/16). */
private const val FACTOR_REGION_CARA_MUNECA = 1.1

/** Multiplicador más estricto para puntos distales de la mano (17/19/21 y 18/20/22). */
private const val FACTOR_REGION_CARA_DEDOS = 0.85

/** Factor de respaldo para estimar ancho facial cuando falta alguna oreja. */
private const val FACTOR_RESPALDO_ANCHO_CARA = 1.6

/** Mínimo ancho facial normalizado para evitar divisiones o radios degenerados. */
private const val ANCHO_CARA_MINIMO = 1e-3

/** Proporción mínima de frames válidos para reportar contacto activo en `mano_cara_conteo` (filtra ruido puntual). */
private const val UMBRAL_PROPORCION_CONTEO = 0.10

private const val DECIMALES_PROPORCION = 4

/**
 * Analiza frame a frame cuántos lados (0, 1 o 2 manos) tocan la región facial en una ventana P-1.
 * Trabaja exclusivamente en RAM y sin persistencia (RNF10 / P-1).
 */
object AnalizadorManoCara {

    fun analizar(ventana: VentanaLandmarksDivlara): DetalleManoCara {
        var framesConCara = 0
        var framesUnaMano = 0
        var framesDosManos = 0

        for (frame in ventana.frames) {
            val manos = contarManosEnCaraEnFrame(frame.landmarks) ?: continue
            framesConCara++
            when (manos) {
                1 -> framesUnaMano++
                2 -> framesDosManos++
            }
        }

        if (framesConCara == 0) {
            return DetalleManoCara(manoCaraUna = 0.0, manoCaraDos = 0.0, manoCaraConteo = 0)
        }

        val proporcionUna = redondear(framesUnaMano.toDouble() / framesConCara.toDouble())
        val proporcionDos = redondear(framesDosManos.toDouble() / framesConCara.toDouble())
        val conteo = resolverConteoDominante(proporcionUna, proporcionDos)

        return DetalleManoCara(
            manoCaraUna = proporcionUna,
            manoCaraDos = proporcionDos,
            manoCaraConteo = conteo
        )
    }

    /** Devuelve 0, 1 o 2 manos en contacto con la cara en el frame, o `null` si no hay rostro fiable. */
    private fun contarManosEnCaraEnFrame(landmarks: List<LandmarkMediaPipe>): Int? {
        if (landmarks.isEmpty()) return null
        val porId = HashMap<Int, LandmarkMediaPipe>(landmarks.size)
        for (lm in landmarks) {
            if (esVisible(lm)) porId[lm.id] = lm
        }

        val regionCara = estimarRegionCara(porId) ?: return null
        val izqToca = manoTocaCara(porId, LM_MUNECA_IZQ, LMS_DEDOS_IZQ, regionCara)
        val derToca = manoTocaCara(porId, LM_MUNECA_DER, LMS_DEDOS_DER, regionCara)
        return (if (izqToca) 1 else 0) + (if (derToca) 1 else 0)
    }

    private fun estimarRegionCara(porId: Map<Int, LandmarkMediaPipe>): RegionCara? {
        var sumaX = 0.0
        var sumaY = 0.0
        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var cantidad = 0

        for (id in LM_CARA_MIN..LM_CARA_MAX) {
            val lm = porId[id] ?: continue
            sumaX += lm.x
            sumaY += lm.y
            if (lm.x < minX) minX = lm.x
            if (lm.x > maxX) maxX = lm.x
            cantidad++
        }
        if (cantidad < MIN_LANDMARKS_CARA) return null

        val orejaIzq = porId[LM_OREJA_IZQ]
        val orejaDer = porId[LM_OREJA_DER]
        val ancho = if (orejaIzq != null && orejaDer != null) {
            hypot(orejaIzq.x - orejaDer.x, orejaIzq.y - orejaDer.y)
        } else {
            (maxX - minX) * FACTOR_RESPALDO_ANCHO_CARA
        }
        if (!ancho.isFinite() || ancho <= ANCHO_CARA_MINIMO) return null

        return RegionCara(
            centroX = sumaX / cantidad.toDouble(),
            centroY = sumaY / cantidad.toDouble(),
            ancho = ancho
        )
    }

    private fun manoTocaCara(
        porId: Map<Int, LandmarkMediaPipe>,
        idMuneca: Int,
        idsDedos: IntArray,
        cara: RegionCara
    ): Boolean {
        val radioMuneca = FACTOR_REGION_CARA_MUNECA * cara.ancho
        val muneca = porId[idMuneca]
        if (muneca != null && hypot(muneca.x - cara.centroX, muneca.y - cara.centroY) < radioMuneca) {
            return true
        }
        val radioDedos = FACTOR_REGION_CARA_DEDOS * cara.ancho
        for (idDedo in idsDedos) {
            val dedo = porId[idDedo] ?: continue
            if (hypot(dedo.x - cara.centroX, dedo.y - cara.centroY) < radioDedos) {
                return true
            }
        }
        return false
    }

    private fun resolverConteoDominante(proporcionUna: Double, proporcionDos: Double): Int = when {
        proporcionDos >= UMBRAL_PROPORCION_CONTEO && proporcionDos >= proporcionUna -> 2
        proporcionUna >= UMBRAL_PROPORCION_CONTEO -> 1
        proporcionDos >= UMBRAL_PROPORCION_CONTEO -> 2
        else -> 0
    }

    private fun esVisible(lm: LandmarkMediaPipe): Boolean =
        lm.x.isFinite() && lm.y.isFinite() && lm.visibility.isFinite() && lm.visibility >= VISIBILIDAD_MINIMA_MANO_CARA

    private fun redondear(valor: Double): Double =
        BigDecimal.valueOf(valor.coerceIn(0.0, 1.0)).setScale(DECIMALES_PROPORCION, RoundingMode.HALF_EVEN).toDouble()

    private data class RegionCara(val centroX: Double, val centroY: Double, val ancho: Double)
}
