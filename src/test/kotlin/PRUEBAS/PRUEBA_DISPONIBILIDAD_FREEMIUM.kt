package PRUEBAS

import CONFIGURACION.EstadoFreemiumReadiness
import CONTROLADORES.controladorSalud
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PruebaDisponibilidadFreemium {
    @Test
    fun `ready informa warming hasta que finaliza el calentamiento y health sigue vivo`() = testApplication {
        val disponibilidad = EstadoFreemiumReadiness(2)
        application { routing { controladorSalud(disponibilidad) } }

        val antes = client.get("/ready")
        assertEquals(HttpStatusCode.ServiceUnavailable, antes.status)
        assertEquals("""{"status":"warming"}""", antes.bodyAsText())
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals("OK", client.get("/health").bodyAsText())

        disponibilidad.completar(LoggerFactory.getLogger("test.readiness")) { }
        val despues = client.get("/ready")
        assertEquals(HttpStatusCode.OK, despues.status)
        assertEquals("""{"status":"ready"}""", despues.bodyAsText())
    }

    @Test
    fun `warmup desactivado deja ready desde el arranque`() = testApplication {
        application { routing { controladorSalud(EstadoFreemiumReadiness(0)) } }

        val respuesta = client.get("/ready")
        assertEquals(HttpStatusCode.OK, respuesta.status)
        assertEquals("""{"status":"ready"}""", respuesta.bodyAsText())
    }

    @Test
    fun `un error durante warmup se registra y no deja ready bloqueado`() = testApplication {
        val disponibilidad = EstadoFreemiumReadiness(1)
        val logger = LoggerFactory.getLogger("test.readiness.failure")
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        (logger as Logger).addAppender(appender)
        try {
            disponibilidad.completar(logger) { error("fallo simulado") }

            assertTrue(disponibilidad.estaListo())
            assertTrue(appender.list.any { it.level.levelStr == "WARN" && it.formattedMessage.contains("fallo simulado") })
            application { routing { controladorSalud(disponibilidad) } }
            assertEquals(HttpStatusCode.OK, client.get("/ready").status)
        } finally {
            (logger as Logger).detachAppender(appender)
        }
    }

}
