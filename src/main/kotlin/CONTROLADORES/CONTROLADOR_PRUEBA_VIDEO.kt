package CONTROLADORES

import ERRORES.ErrorValidacion
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Peso máximo de un vídeo de prueba: 200 MB. */
const val MAXIMO_BYTES_VIDEO_PRUEBA: Long = 200L * 1024 * 1024

/**
 * Tope del parser multipart de Ktor (`formFieldLimit`, por defecto 50 MB). Se aplica antes del handler, así que debe
 * ser mayor que [MAXIMO_BYTES_VIDEO_PRUEBA] para que el chequeo propio (400) sea el que decida: 210 MB.
 */
const val LIMITE_MULTIPART_VIDEO_PRUEBA: Long = 210L * 1024 * 1024

private val FORMATO_MARCA_TIEMPO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

/**
 * Recepción de vídeos de prueba (one-off para calibrar umbrales del modelo). Sin sesión, solo para entorno de pruebas.
 * POST /pruebas/video  multipart, campo `video` (.mp4, máx. 200 MB) → { ok, archivo, bytes }
 * GET  /pruebas        lista los vídeos guardados: [{ archivo, bytes, fecha }]
 * El nombre del archivo lo genera el servidor; el nombre que manda el cliente se ignora (sin path traversal).
 */
fun Route.controladorPruebaVideo(
    carpeta: File = File("pruebas"),
    maximoBytes: Long = MAXIMO_BYTES_VIDEO_PRUEBA
) {
    post("/pruebas/video") {
        var guardado: Pair<File, Long>? = null
        try {
            call.receiveMultipart(LIMITE_MULTIPART_VIDEO_PRUEBA).forEachPart { parte ->
                try {
                    if (parte is PartData.FileItem && parte.name == "video" && guardado == null) {
                        val nombreCliente = parte.originalFileName.orEmpty()
                        if (!nombreCliente.lowercase().endsWith(".mp4")) {
                            throw ErrorValidacion("extension_invalida", "El archivo debe ser .mp4")
                        }
                        guardado = guardarVideo(parte, carpeta, maximoBytes)
                    }
                } finally {
                    parte.dispose()
                }
            }
        } catch (e: IOException) {
            throw ErrorValidacion("video_ilegible", "No se pudo leer el vídeo enviado")
        }
        val (archivo, bytes) = guardado
            ?: throw ErrorValidacion("video_requerido", "Falta el campo multipart 'video'")
        call.respondText(
            """{"ok":true,"archivo":"pruebas/${archivo.name}","bytes":$bytes}""",
            ContentType.Application.Json, HttpStatusCode.OK
        )
    }

    get("/pruebas") {
        val archivos = withContext(Dispatchers.IO) {
            carpeta.listFiles { f -> f.isFile && f.name.endsWith(".mp4") }.orEmpty().sortedBy { it.name }
        }
        val cuerpo = archivos.joinToString(",", "[", "]") { f ->
            """{"archivo":"${f.name}","bytes":${f.length()},"fecha":"${Instant.ofEpochMilli(f.lastModified())}"}"""
        }
        call.respondText(cuerpo, ContentType.Application.Json, HttpStatusCode.OK)
    }
}

/** Copia el vídeo con un tope de peso; si lo excede, borra lo escrito y responde 400. */
private suspend fun guardarVideo(parte: PartData.FileItem, carpeta: File, maximoBytes: Long): Pair<File, Long> =
    withContext(Dispatchers.IO) {
        carpeta.mkdirs()
        val destino = File(carpeta, "prueba-${LocalDateTime.now(ZoneOffset.UTC).format(FORMATO_MARCA_TIEMPO)}.mp4")
        var total = 0L
        try {
            destino.outputStream().use { salida ->
                val buffer = ByteArray(64 * 1024)
                val canal = parte.provider()
                while (true) {
                    val leidos = canal.readAvailable(buffer, 0, buffer.size)
                    if (leidos < 0) break
                    total += leidos
                    if (total > maximoBytes) {
                        throw ErrorValidacion("video_demasiado_grande", "El vídeo supera el máximo de ${maximoBytes / (1024 * 1024)} MB")
                    }
                    salida.write(buffer, 0, leidos)
                }
            }
        } catch (e: Throwable) {
            destino.delete()
            throw e
        }
        destino to total
    }
