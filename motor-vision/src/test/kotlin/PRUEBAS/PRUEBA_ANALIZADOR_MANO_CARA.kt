package PRUEBAS

import CONFIGURACION.AnalizadorManoCara
import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.FrameLandmarksDivlara
import CONFIGURACION.LandmarkMediaPipe
import CONFIGURACION.MotorInferenciaDivlara
import CONFIGURACION.ResultadoPrediccionDivlara
import CONFIGURACION.VERSION_MODELO_DIVLARA
import CONFIGURACION.VentanaLandmarksDivlara
import SERVICIOS.ServicioVision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PruebaAnalizadorManoCara {

    private fun crearFrame(
        indice: Int,
        manoIzqEnCara: Boolean = false,
        manoDerEnCara: Boolean = false,
        soloDedosIzqEnCara: Boolean = false,
        visibilidadManos: Double = 0.95
    ): FrameLandmarksDivlara {
        // Cara centrada en (0.50, 0.25) con ancho entre orejas = 0.10 (radio muñeca = 0.11, radio dedos = 0.085).
        val lms = mutableListOf(
            LandmarkMediaPipe(0, 0.50, 0.25, -0.20, 0.95),
            LandmarkMediaPipe(2, 0.47, 0.23, -0.18, 0.95),
            LandmarkMediaPipe(5, 0.53, 0.23, -0.18, 0.95),
            LandmarkMediaPipe(7, 0.45, 0.25, -0.15, 0.95),
            LandmarkMediaPipe(8, 0.55, 0.25, -0.15, 0.95),
            LandmarkMediaPipe(11, 0.40, 0.45, -0.10, 0.95),
            LandmarkMediaPipe(12, 0.60, 0.45, -0.10, 0.95),
            LandmarkMediaPipe(23, 0.43, 0.80, -0.02, 0.95),
            LandmarkMediaPipe(24, 0.57, 0.80, -0.02, 0.95)
        )
        // Muñeca izquierda (15) e índice izquierdo (19)
        if (manoIzqEnCara) {
            lms += LandmarkMediaPipe(15, 0.45, 0.30, -0.20, visibilidadManos)
        } else {
            lms += LandmarkMediaPipe(15, 0.35, 0.60, -0.10, visibilidadManos)
        }
        if (soloDedosIzqEnCara) {
            lms += LandmarkMediaPipe(19, 0.47, 0.28, -0.20, visibilidadManos)
        }
        // Muñeca derecha (16)
        if (manoDerEnCara) {
            lms += LandmarkMediaPipe(16, 0.55, 0.30, -0.20, visibilidadManos)
        } else {
            lms += LandmarkMediaPipe(16, 0.65, 0.60, -0.10, visibilidadManos)
        }
        return FrameLandmarksDivlara(indiceFrame = indice, landmarks = lms, timestampMs = indice * 100L)
    }

    @Test
    fun `sin manos en la cara devuelve ceros y conteo cero`() {
        val ventana = VentanaLandmarksDivlara((0 until 20).map { crearFrame(it) })
        val detalle = AnalizadorManoCara.analizar(ventana)

        assertEquals(0.0, detalle.manoCaraUna)
        assertEquals(0.0, detalle.manoCaraDos)
        assertEquals(0, detalle.manoCaraConteo)
    }

    @Test
    fun `distingue una sola mano izquierda o derecha en la cara`() {
        val soloIzq = AnalizadorManoCara.analizar(
            VentanaLandmarksDivlara((0 until 20).map { crearFrame(it, manoIzqEnCara = true) })
        )
        assertEquals(1.0, soloIzq.manoCaraUna)
        assertEquals(0.0, soloIzq.manoCaraDos)
        assertEquals(1, soloIzq.manoCaraConteo)

        val soloDer = AnalizadorManoCara.analizar(
            VentanaLandmarksDivlara((0 until 20).map { crearFrame(it, manoDerEnCara = true) })
        )
        assertEquals(1.0, soloDer.manoCaraUna)
        assertEquals(0.0, soloDer.manoCaraDos)
        assertEquals(1, soloDer.manoCaraConteo)
    }

    @Test
    fun `detecta toque de una mano usando puntos distales cuando la muneca queda fuera del radio`() {
        val detalle = AnalizadorManoCara.analizar(
            VentanaLandmarksDivlara((0 until 10).map { crearFrame(it, soloDedosIzqEnCara = true) })
        )

        assertEquals(1.0, detalle.manoCaraUna)
        assertEquals(0.0, detalle.manoCaraDos)
        assertEquals(1, detalle.manoCaraConteo)
    }

    @Test
    fun `distingue ambas manos simultaneas en la cara frente a una sola mano`() {
        val ventana = VentanaLandmarksDivlara(
            (0 until 20).map { crearFrame(it, manoIzqEnCara = true, manoDerEnCara = true) }
        )
        val detalle = AnalizadorManoCara.analizar(ventana)

        assertEquals(0.0, detalle.manoCaraUna)
        assertEquals(1.0, detalle.manoCaraDos)
        assertEquals(2, detalle.manoCaraConteo)
    }

    // D46: el contacto debe marcarse casi de inmediato; con 2 de 20 fotogramas (0,2 s) ya cuenta, con 1 es ruido.
    @Test
    fun `contacto breve de 2 de 20 fotogramas ya cuenta una mano o ambas`() {
        fun ventana(izq: Boolean, der: Boolean, n: Int) = VentanaLandmarksDivlara(
            (0 until 20).map { crearFrame(it, manoIzqEnCara = izq && it < n, manoDerEnCara = der && it < n) }
        )
        assertEquals(1, AnalizadorManoCara.analizar(ventana(izq = true, der = false, n = 2)).manoCaraConteo)
        assertEquals(2, AnalizadorManoCara.analizar(ventana(izq = true, der = true, n = 2)).manoCaraConteo)
        assertEquals(0, AnalizadorManoCara.analizar(ventana(izq = true, der = false, n = 1)).manoCaraConteo)
    }

    @Test
    fun `calcula proporciones exactas y conteo dominante en ventana mixta`() {
        val frames = buildList {
            repeat(6) { add(crearFrame(it, manoIzqEnCara = true)) }
            repeat(10) { add(crearFrame(6 + it, manoIzqEnCara = true, manoDerEnCara = true)) }
            repeat(4) { add(crearFrame(16 + it)) }
        }
        val detalle = AnalizadorManoCara.analizar(VentanaLandmarksDivlara(frames))

        assertEquals(0.3, detalle.manoCaraUna)
        assertEquals(0.5, detalle.manoCaraDos)
        assertEquals(2, detalle.manoCaraConteo)
    }

    @Test
    fun `ignora manos con baja visibilidad y filtra un frame aislado de ruido en el conteo`() {
        val bajaVis = AnalizadorManoCara.analizar(
            VentanaLandmarksDivlara((0 until 10).map { crearFrame(it, manoIzqEnCara = true, visibilidadManos = 0.2) })
        )
        assertEquals(0.0, bajaVis.manoCaraUna)
        assertEquals(0, bajaVis.manoCaraConteo)

        val ruidoAislado = AnalizadorManoCara.analizar(
            VentanaLandmarksDivlara((0 until 20).map { crearFrame(it, manoIzqEnCara = (it == 0)) })
        )
        assertEquals(0.05, ruidoAislado.manoCaraUna)
        assertEquals(0.0, ruidoAislado.manoCaraDos)
        assertEquals(0, ruidoAislado.manoCaraConteo)
    }

    @Test
    fun `ServicioVision agrega desagregacion en ventanas P-1 y la omite en modo features v1`() {
        val motorStub = object : MotorInferenciaDivlara {
            override fun cantidadFeaturesEsperadas(): Int = CANTIDAD_FEATURES_DIVLARA
            override fun predecir(features: List<Double>): ResultadoPrediccionDivlara =
                ResultadoPrediccionDivlara(VERSION_MODELO_DIVLARA, mapOf("hand_to_face_adaptors" to 3.0))
        }
        val servicio = ServicioVision(motorStub)

        val respFeatures = servicio.predecir(List(CANTIDAD_FEATURES_DIVLARA) { 0.0 })
        assertNull(respFeatures.manoCaraUna)
        assertNull(respFeatures.manoCaraDos)
        assertNull(respFeatures.manoCaraConteo)
        assertEquals(mapOf("hand_to_face_adaptors" to 3.0), respFeatures.predicciones)

        val respVentana = servicio.predecir(
            VentanaLandmarksDivlara((0 until 20).map { crearFrame(it, manoIzqEnCara = true) })
        )
        assertEquals(mapOf("hand_to_face_adaptors" to 3.0), respVentana.predicciones)
        assertEquals(1.0, respVentana.manoCaraUna)
        assertEquals(0.0, respVentana.manoCaraDos)
        assertEquals(1, respVentana.manoCaraConteo)
    }
}
