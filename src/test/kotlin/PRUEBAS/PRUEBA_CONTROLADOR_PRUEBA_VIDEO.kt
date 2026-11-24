package PRUEBAS

import CONFIGURACION.configurarErrores
import CONTROLADORES.controladorPruebaVideo
import CONTROLADORES.controladorPruebaVideoSiHabilitado
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Recepción de vídeos de prueba: guarda con nombre del servidor y rechaza extensión o peso inválidos. */
class PruebaControladorPruebaVideo {

    private fun ApplicationTestBuilder.montar(carpeta: java.io.File, maximo: Long) = application {
        install(ContentNegotiation) { json() }
        configurarErrores()
        routing { controladorPruebaVideo(carpeta, maximo) }
    }

    private suspend fun ApplicationTestBuilder.subir(nombre: String, bytes: ByteArray) =
        client.submitFormWithBinaryData("/pruebas/video", formData {
            append("video", bytes, Headers.build {
                append(HttpHeaders.ContentType, "video/mp4")
                append(HttpHeaders.ContentDisposition, "filename=\"$nombre\"")
            })
        })

    @Test
    fun `sin HABILITAR_PRUEBAS_VIDEO las rutas no existen y no se guarda nada (D39)`() = testApplication {
        val carpeta = Files.createTempDirectory("pruebas-video").toFile().resolve("pruebas")
        try {
            application {
                install(ContentNegotiation) { json() }
                configurarErrores()
                routing { controladorPruebaVideoSiHabilitado(false, carpeta, 1024) }
            }
            assertEquals(HttpStatusCode.NotFound, subir("prueba.mp4", ByteArray(10)).status)
            assertEquals(HttpStatusCode.NotFound, client.get("/pruebas").status)
            assertFalse(carpeta.exists(), "no se crea la carpeta ni se escribe nada")
        } finally {
            carpeta.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `con HABILITAR_PRUEBAS_VIDEO las rutas se montan`() = testApplication {
        val carpeta = Files.createTempDirectory("pruebas-video").toFile().resolve("pruebas")
        try {
            application {
                install(ContentNegotiation) { json() }
                configurarErrores()
                routing { controladorPruebaVideoSiHabilitado(true, carpeta, 1024) }
            }
            assertEquals(HttpStatusCode.OK, subir("prueba.mp4", ByteArray(10)).status)
            assertEquals(1, carpeta.listFiles().orEmpty().size)
        } finally {
            carpeta.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `una subida valida guarda el archivo con nombre del servidor y aparece en el listado`() = testApplication {
        val carpeta = Files.createTempDirectory("pruebas-video").toFile().resolve("pruebas")
        try {
            montar(carpeta, 1024)
            val contenido = ByteArray(500) { it.toByte() }
            val respuesta = subir("../../evil.mp4", contenido)
            assertEquals(HttpStatusCode.OK, respuesta.status)
            val cuerpo = respuesta.bodyAsText()
            assertTrue(cuerpo.contains(""""ok":true"""), cuerpo)
            assertTrue(cuerpo.contains(""""bytes":500"""), cuerpo)
            val guardados = carpeta.listFiles().orEmpty()
            assertEquals(1, guardados.size)
            assertTrue(guardados[0].name.matches(Regex("""prueba-.*.mp4""")))
            assertTrue(guardados[0].readBytes().contentEquals(contenido))
            assertTrue(client.get("/pruebas").bodyAsText().contains(guardados[0].name))
        } finally {
            carpeta.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `rechaza con 400 un archivo que supera el maximo y no deja residuos`() = testApplication {
        val carpeta = Files.createTempDirectory("pruebas-video").toFile().resolve("pruebas")
        try {
            montar(carpeta, 1024)
            val respuesta = subir("grande.mp4", ByteArray(2048))
            assertEquals(HttpStatusCode.BadRequest, respuesta.status)
            assertEquals(0, carpeta.listFiles().orEmpty().size)
        } finally {
            carpeta.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `rechaza con 400 una extension que no es mp4`() = testApplication {
        val carpeta = Files.createTempDirectory("pruebas-video").toFile().resolve("pruebas")
        try {
            montar(carpeta, 1024)
            assertEquals(HttpStatusCode.BadRequest, subir("video.avi", ByteArray(10)).status)
            assertEquals(0, carpeta.listFiles().orEmpty().size)
        } finally {
            carpeta.parentFile.deleteRecursively()
        }
    }
}
