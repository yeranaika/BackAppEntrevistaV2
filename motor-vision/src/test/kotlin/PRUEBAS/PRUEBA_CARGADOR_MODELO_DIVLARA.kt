package PRUEBAS

import CONFIGURACION.ARCHIVO_META_DIVLARA
import CONFIGURACION.ARCHIVO_SCALER_DIVLARA
import CONFIGURACION.ARTEFACTO_MODELO_DIVLARA
import CONFIGURACION.ArtefactoModeloDivlara
import CONFIGURACION.CANTIDAD_FEATURES_DIVLARA
import CONFIGURACION.CargadorModeloDivlara
import CONFIGURACION.DIRECTORIO_MODELOS_DIVLARA
import CONFIGURACION.ESCALA_MODELO_DIVLARA
import CONFIGURACION.EstadoCargaModeloDivlara
import CONFIGURACION.LectorArtefactoArchivoPreparado
import CONFIGURACION.LectorArtefactoModeloDivlara
import CONFIGURACION.VERSION_MODELO_DIVLARA
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebaCargadorModeloDivlara {

    @Serializable
    private data class GoldenDivlara(
        val features: List<Double>,
        val expected: Map<String, Double>,
        val tolerancia: Double
    )

    private val instanteFijo = Instant.parse("2026-11-19T14:30:00Z")
    private val relojFijo = Clock.fixed(instanteFijo, ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true }

    private fun conDirectorioTemporal(bloque: (Path) -> Unit) {
        val dir = Files.createTempDirectory("divlara-test-")
        try {
            bloque(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    /** Crea el paquete mínimo de 3 archivos (`modelo.onnx`, `scaler.json`, `meta.json`) en un directorio temporal. */
    private fun escribirPaqueteDummy(
        dir: Path,
        contenidoOnnx: ByteArray = "ONNX_DUMMY_FIXTURE_DIVLARA".toByteArray(),
        incluirScaler: Boolean = true,
        incluirMeta: Boolean = true
    ): Path {
        val carpeta = dir.resolve(VERSION_MODELO_DIVLARA)
        Files.createDirectories(carpeta)
        val archivo = carpeta.resolve(ARTEFACTO_MODELO_DIVLARA)
        Files.write(archivo, contenidoOnnx)
        if (incluirScaler) {
            Files.writeString(carpeta.resolve(ARCHIVO_SCALER_DIVLARA), """{"mean":[0.0],"scale":[1.0]}""")
        }
        if (incluirMeta) {
            Files.writeString(
                carpeta.resolve(ARCHIVO_META_DIVLARA),
                """{"feature_columns":["f0"],"targets":["posture_stability"]}"""
            )
        }
        return archivo
    }

    @Test
    fun `archivo ausente deja estado NO_DISPONIBLE sin fallar y sin llamar al lector`() = conDirectorioTemporal { dir ->
        val invocaciones = AtomicInteger(0)
        val cargador = CargadorModeloDivlara(
            directorioBase = dir,
            lector = LectorArtefactoModeloDivlara {
                invocaciones.incrementAndGet()
                error("no debería llamarse si el archivo no existe")
            },
            reloj = relojFijo
        )

        val estado = cargador.cargarSiDisponible()

        assertEquals(VERSION_MODELO_DIVLARA, estado.version)
        assertEquals(EstadoCargaModeloDivlara.NO_DISPONIBLE, estado.estado)
        assertFalse(estado.cargado)
        assertNull(estado.tamanoBytes)
        assertNull(estado.fechaCarga)
        assertNull(estado.error)
        assertNull(cargador.artefacto())
        assertEquals(0, invocaciones.get())
    }

    @Test
    fun `falta de scaler o meta deja estado NO_DISPONIBLE sin llamar al lector`() = conDirectorioTemporal { dir ->
        escribirPaqueteDummy(dir, incluirScaler = false, incluirMeta = true)
        val invocaciones = AtomicInteger(0)
        val cargador = CargadorModeloDivlara(
            directorioBase = dir,
            lector = LectorArtefactoModeloDivlara {
                invocaciones.incrementAndGet()
                error("no debería llamarse si falta scaler.json")
            },
            reloj = relojFijo
        )

        val estado = cargador.cargarSiDisponible()
        assertEquals(EstadoCargaModeloDivlara.NO_DISPONIBLE, estado.estado)
        assertFalse(estado.cargado)
        assertNull(estado.error)
        assertEquals(0, invocaciones.get())
    }

    @Test
    fun `fixture dummy carga el modelo y expone version tamano y fecha de carga`() = conDirectorioTemporal { dir ->
        val contenido = "ONNX_DUMMY_FIXTURE_DIVLARA".toByteArray()
        val archivo = escribirPaqueteDummy(dir, contenidoOnnx = contenido)

        val cargador = CargadorModeloDivlara(
            directorioBase = dir,
            lector = LectorArtefactoArchivoPreparado(),
            reloj = relojFijo
        )
        cargador.use {
            val estado = it.cargarSiDisponible()

            assertEquals(VERSION_MODELO_DIVLARA, estado.version)
            assertEquals(EstadoCargaModeloDivlara.CARGADO, estado.estado)
            assertTrue(estado.cargado)
            assertEquals(contenido.size.toLong(), estado.tamanoBytes)
            assertEquals(instanteFijo, estado.fechaCarga)
            assertNull(estado.error)
            assertNotNull(it.artefacto())
            assertEquals(archivo, it.artefacto()?.ruta)
        }
    }

    @Test
    fun `carga una sola vez aunque se invoque repetidamente y close libera el artefacto`() = conDirectorioTemporal { dir ->
        escribirPaqueteDummy(dir, contenidoOnnx = byteArrayOf(1, 2, 3, 4))

        val invocaciones = AtomicInteger(0)
        val cerrado = AtomicBoolean(false)
        val stubLector = LectorArtefactoModeloDivlara { ruta ->
            invocaciones.incrementAndGet()
            object : ArtefactoModeloDivlara {
                override val ruta: Path = ruta
                override val tamanoBytes: Long = 4L
                override fun close() {
                    cerrado.set(true)
                }
            }
        }

        val cargador = CargadorModeloDivlara(directorioBase = dir, lector = stubLector, reloj = relojFijo)
        val primero = cargador.cargarSiDisponible()
        val segundo = cargador.cargarSiDisponible()

        assertEquals(1, invocaciones.get())
        assertEquals(primero, segundo)
        assertTrue(cargador.estado().cargado)

        cargador.close()
        assertTrue(cerrado.get())
        assertNull(cargador.artefacto())
    }

    @Test
    fun `archivo vacio falla de forma controlada sin romper el arranque`() = conDirectorioTemporal { dir ->
        escribirPaqueteDummy(dir, contenidoOnnx = ByteArray(0))

        val cargador = CargadorModeloDivlara(directorioBase = dir, reloj = relojFijo)
        val estado = cargador.cargarSiDisponible()

        assertEquals(EstadoCargaModeloDivlara.ERROR, estado.estado)
        assertFalse(estado.cargado)
        assertEquals(0L, estado.tamanoBytes)
        assertNull(estado.fechaCarga)
        assertTrue(estado.error?.contains("vacío") == true)
        assertNull(cargador.artefacto())
    }

    @Test
    fun `onnx corrupto falla de forma controlada con lector ONNX real`() = conDirectorioTemporal { dir ->
        escribirPaqueteDummy(dir, contenidoOnnx = "NO_ES_UN_ONNX_VALIDO".toByteArray())

        val cargador = CargadorModeloDivlara(directorioBase = dir, reloj = relojFijo)
        val estado = cargador.cargarSiDisponible()

        assertEquals(EstadoCargaModeloDivlara.ERROR, estado.estado)
        assertFalse(estado.cargado)
        assertNotNull(estado.error)
        assertNull(cargador.artefacto())
    }

    @Test
    fun `error del lector stub registra el fallo y mantiene el servicio vivo`() = conDirectorioTemporal { dir ->
        escribirPaqueteDummy(dir, contenidoOnnx = byteArrayOf(9, 8, 7))

        val cargador = CargadorModeloDivlara(
            directorioBase = dir,
            lector = LectorArtefactoModeloDivlara { throw IllegalStateException("formato onnx corrupto") },
            reloj = relojFijo
        )

        val estado = cargador.cargarSiDisponible()

        assertEquals(EstadoCargaModeloDivlara.ERROR, estado.estado)
        assertFalse(estado.cargado)
        assertEquals("formato onnx corrupto", estado.error)
        assertEquals(3L, estado.tamanoBytes)
        assertNull(estado.fechaCarga)
    }

    @Test
    fun `modelo E14 real carga con ONNX Runtime y cumple paridad numerica contra golden json`() {
        val goldenPath = Path.of(DIRECTORIO_MODELOS_DIVLARA, VERSION_MODELO_DIVLARA, "golden.json")
        assertTrue(Files.isRegularFile(goldenPath), "Debe existir $goldenPath")
        val golden = json.decodeFromString<GoldenDivlara>(Files.readString(goldenPath))
        assertEquals(CANTIDAD_FEATURES_DIVLARA, golden.features.size)

        CargadorModeloDivlara(reloj = relojFijo).use { cargador ->
            val estado = cargador.cargarSiDisponible()
            assertEquals(EstadoCargaModeloDivlara.CARGADO, estado.estado, "Error al cargar E14: ${estado.error}")
            assertTrue(estado.cargado)
            assertEquals(CANTIDAD_FEATURES_DIVLARA, cargador.cantidadFeaturesEsperadas())

            val resultado = cargador.predecir(golden.features)
            assertEquals(VERSION_MODELO_DIVLARA, resultado.versionModelo)
            assertEquals(ESCALA_MODELO_DIVLARA, resultado.escala)
            assertEquals(golden.expected.keys, resultado.predicciones.keys)

            golden.expected.forEach { (variable, esperado) ->
                val real = assertNotNull(resultado.predicciones[variable], "Falta predicción para $variable")
                val delta = abs(real - esperado)
                assertTrue(
                    delta <= golden.tolerancia,
                    "Paridad fuera de tolerancia en $variable: esperado=$esperado, real=$real, delta=$delta > ${golden.tolerancia}"
                )
            }
        }
    }
}
