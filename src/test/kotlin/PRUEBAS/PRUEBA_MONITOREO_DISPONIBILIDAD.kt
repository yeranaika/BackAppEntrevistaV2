package PRUEBAS

import CONFIGURACION.configurarMonitoreo
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PruebaMonitoreoDisponibilidad {
    @Test
    fun `call logging omite health ready y evaluacion freemium`() = testApplication {
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            application {
                configurarMonitoreo()
                routing {
                    get("/health") { call.respondText("OK") }
                    get("/ready") { call.respondText("OK") }
                    get("/api/v1/practice/evaluate-freemium") { call.respondText("OK") }
                    get("/visible") { call.respondText("OK") }
                }
            }
            client.get("/health")
            client.get("/ready")
            client.get("/api/v1/practice/evaluate-freemium")
            client.get("/visible")

            val mensajes = appender.list.map { it.formattedMessage }
            assertFalse(mensajes.any { it.contains("/health") || it.contains("/ready") || it.contains("evaluate-freemium") })
            assertTrue(mensajes.any { it.contains("/visible") })
        } finally {
            logger.detachAppender(appender)
        }
    }

    @Test
    fun `call logging omite query string cabeceras y cuerpo de solicitud`() = testApplication {
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            application {
                configurarMonitoreo()
                routing {
                    get("/consulta") { call.respondText("OK") }
                    post("/api/v1/entrevistas/1/metricas") {
                        call.receiveText()
                        call.respondText("OK")
                    }
                }
            }
            client.get("/consulta?code=secreto_oauth&token=jwt_privado") {
                header(HttpHeaders.Authorization, "Bearer token_super_secreto")
            }
            client.post("/api/v1/entrevistas/1/metricas") {
                contentType(ContentType.Application.Json)
                setBody("""{"datos_biometricos":[0.12,0.34],"transcripcion":"respuesta privada del candidato"}""")
            }

            val mensajes = appender.list.map { it.formattedMessage }
            assertTrue(mensajes.any { it.contains("GET - /consulta") })
            assertTrue(mensajes.any { it.contains("POST - /api/v1/entrevistas/1/metricas") })
            assertFalse(mensajes.any { it.contains("secreto_oauth") || it.contains("jwt_privado") || it.contains("token_super_secreto") })
            assertFalse(mensajes.any { it.contains("datos_biometricos") || it.contains("respuesta privada del candidato") })
        } finally {
            logger.detachAppender(appender)
        }
    }
}
