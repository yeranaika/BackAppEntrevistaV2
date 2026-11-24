package PRUEBAS

import CONFIGURACION.configurarErrores
import CONTROLADORES.controladorPruebaVideo
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
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
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** El límite multipart de Ktor (50 MB por defecto) no debe cortar vídeos entre 50 y 200 MB con un 500. */
class PruebaControladorPruebaVideoLimite {

    private fun ApplicationTestBuilder.montar(carpeta: File, maximo: Long) = application {
        install(ContentNegotiation) { json() }
        configurarErrores()
        routing { controladorPruebaVideo(carpeta, maximo) }
    }

    private suspend fun ApplicationTestBuilder.subir(bytes: ByteArray) =
        client.submitFormWithBinaryData("/pruebas/video", formData {
            append("video", bytes, Headers.build {
                append(HttpHeaders.ContentType, "video/mp4")
                append(HttpHeaders.ContentDisposition, "filename=\"grande.mp4\"")
            })
        })

    @Test
    fun `acepta un video de 51 MB que supera el limite por defecto de Ktor`() = testApplication {
        val carpeta = Files.createTempDirectory("pruebas-video").toFile().resolve("pruebas")
        try {
            montar(carpeta, 200L * 1024 * 1024)
            val tamano = 51 * 1024 * 1024
            val respuesta = subir(ByteArray(tamano))
            assertEquals(HttpStatusCode.OK, respuesta.status, respuesta.bodyAsText())
            assertEquals(tamano.toLong(), carpeta.listFiles().orEmpty().single().length())
        } finally {
            carpeta.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `rechaza con 400 un video que supera el maximo del handler sin llegar a 500`() = testApplication {
        val carpeta = Files.createTempDirectory("pruebas-video").toFile().resolve("pruebas")
        try {
            montar(carpeta, 51L * 1024 * 1024)
            val respuesta = subir(ByteArray(52 * 1024 * 1024))
            assertEquals(HttpStatusCode.BadRequest, respuesta.status)
            assertTrue(respuesta.bodyAsText().contains("video_demasiado_grande"))
            assertEquals(0, carpeta.listFiles().orEmpty().size)
        } finally {
            carpeta.parentFile.deleteRecursively()
        }
    }
}
