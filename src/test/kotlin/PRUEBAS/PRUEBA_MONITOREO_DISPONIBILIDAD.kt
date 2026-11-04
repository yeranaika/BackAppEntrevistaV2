package PRUEBAS

import CONFIGURACION.configurarMonitoreo
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.get
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
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
}
