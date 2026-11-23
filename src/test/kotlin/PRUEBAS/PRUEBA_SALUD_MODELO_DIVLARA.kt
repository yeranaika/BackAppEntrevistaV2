package PRUEBAS

import CONFIGURACION.ARCHIVO_META_DIVLARA
import CONFIGURACION.ARCHIVO_SCALER_DIVLARA
import CONFIGURACION.ARTEFACTO_MODELO_DIVLARA
import CONFIGURACION.CargadorModeloDivlara
import CONFIGURACION.DIRECTORIO_MODELOS_DIVLARA
import CONFIGURACION.EstadoFreemiumReadiness
import CONFIGURACION.LectorArtefactoArchivoPreparado
import CONFIGURACION.LectorArtefactoModeloDivlara
import CONFIGURACION.VERSION_MODELO_DIVLARA
import CONTROLADORES.controladorSalud
import MODELOS.GESTOS_OBSERVABLES
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `/health` del backend frente a los estados del modelo y coherencia del manifiesto con los gestos del dominio.
 * Usa el cargador real del motor (dependencia solo de pruebas): en producción el backend no lo carga (T3.11).
 */
class PruebaSaludModeloDivlara {

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
    fun `manifiesto del repositorio coincide con la version artefacto y gestos observables`() {
        val manifiestoPath = Path.of(DIRECTORIO_MODELOS_DIVLARA, VERSION_MODELO_DIVLARA, "MANIFIESTO.json")
        assertTrue(Files.isRegularFile(manifiestoPath), "Debe existir $manifiestoPath")

        val doc = Json.parseToJsonElement(Files.readString(manifiestoPath)).jsonObject
        assertEquals(VERSION_MODELO_DIVLARA, doc["version"]?.jsonPrimitive?.content)
        assertEquals(ARTEFACTO_MODELO_DIVLARA, doc["artefacto"]?.jsonObject?.get("archivo")?.jsonPrimitive?.content)
        assertNotNull(doc["entrada"]?.jsonObject)
        assertNotNull(doc["normalizacion"]?.jsonObject)

        val salidas = doc["salidas"]?.jsonObject
        assertNotNull(salidas)
        assertEquals(true, salidas["sinEmociones"]?.jsonPrimitive?.boolean)
        val gestosManifiesto = salidas["gestos"]?.jsonObject?.get("clavesPermitidas")
            ?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
        assertEquals(GESTOS_OBSERVABLES, gestosManifiesto)
    }

    @Test
    fun `health sin modelo responde 200 con modeloDivlara cargado false y sin error`() = conDirectorioTemporal { dir ->
        val cargador = CargadorModeloDivlara(directorioBase = dir, reloj = relojFijo).apply { cargarSiDisponible() }

        testApplication {
            application { routing { controladorSalud(EstadoFreemiumReadiness(0), cargador) } }

            val respuesta = client.get("/health")
            assertEquals(HttpStatusCode.OK, respuesta.status)
            assertEquals(ContentType.Application.Json, respuesta.contentType()?.withoutParameters())

            val cuerpo = Json.parseToJsonElement(respuesta.bodyAsText()).jsonObject
            assertEquals("OK", cuerpo["status"]?.jsonPrimitive?.content)
            val modelo = cuerpo["modeloDivlara"]?.jsonObject
            assertNotNull(modelo)
            assertEquals(VERSION_MODELO_DIVLARA, modelo["version"]?.jsonPrimitive?.content)
            assertFalse(modelo["cargado"]?.jsonPrimitive?.boolean ?: true)
            assertFalse("error" in modelo)
        }
    }

    @Test
    fun `health con modelo dummy cargado responde 200 con modeloDivlara cargado true`() = conDirectorioTemporal { dir ->
        escribirPaqueteDummy(dir, contenidoOnnx = "DUMMY_MODELO_BYTES".toByteArray())

        val cargador = CargadorModeloDivlara(
            directorioBase = dir,
            lector = LectorArtefactoArchivoPreparado(),
            reloj = relojFijo
        ).apply { cargarSiDisponible() }
        cargador.use {
            testApplication {
                application { routing { controladorSalud(EstadoFreemiumReadiness(0), it) } }

                val respuesta = client.get("/health")
                assertEquals(HttpStatusCode.OK, respuesta.status)

                val cuerpo = Json.parseToJsonElement(respuesta.bodyAsText()).jsonObject
                assertEquals("OK", cuerpo["status"]?.jsonPrimitive?.content)
                val modelo = cuerpo["modeloDivlara"]?.jsonObject
                assertNotNull(modelo)
                assertEquals(VERSION_MODELO_DIVLARA, modelo["version"]?.jsonPrimitive?.content)
                assertTrue(modelo["cargado"]?.jsonPrimitive?.boolean ?: false)
                assertFalse("error" in modelo)
            }
        }
    }

    @Test
    fun `health con error de carga sigue respondiendo 200 e informa modeloDivlara error`() = conDirectorioTemporal { dir ->
        escribirPaqueteDummy(dir, contenidoOnnx = byteArrayOf(42))

        val cargador = CargadorModeloDivlara(
            directorioBase = dir,
            lector = LectorArtefactoModeloDivlara { throw IllegalStateException("checksum invalido") },
            reloj = relojFijo
        ).apply { cargarSiDisponible() }

        testApplication {
            application { routing { controladorSalud(EstadoFreemiumReadiness(0), cargador) } }

            val respuesta = client.get("/health")
            assertEquals(HttpStatusCode.OK, respuesta.status)

            val cuerpo = Json.parseToJsonElement(respuesta.bodyAsText()).jsonObject
            assertEquals("OK", cuerpo["status"]?.jsonPrimitive?.content)
            val modelo = cuerpo["modeloDivlara"]?.jsonObject
            assertNotNull(modelo)
            assertEquals(VERSION_MODELO_DIVLARA, modelo["version"]?.jsonPrimitive?.content)
            assertFalse(modelo["cargado"]?.jsonPrimitive?.boolean ?: true)
            assertEquals("checksum invalido", modelo["error"]?.jsonPrimitive?.content)
        }
    }
}
