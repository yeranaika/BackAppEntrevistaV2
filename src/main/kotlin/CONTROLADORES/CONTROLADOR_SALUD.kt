package CONTROLADORES

import ESQUEMAS.RespuestaError
import UTILIDADES.transaccion
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("CONTROLADOR_SALUD")

/**
 * GET /health   "OK" si la base de datos responde; 503 si no (para el supervisor / balanceador).
 * Redis no se incluye: la aplicación funciona sin caché.
 */
fun Route.controladorSalud() {
    get("/health") {
        val baseDisponible = try {
            transaccion { TransactionManager.current().exec("SELECT 1") { it.next() } } == true
        } catch (e: Exception) {
            log.error("La base de datos no responde", e)
            false
        }
        if (baseDisponible) {
            call.respondText("OK")
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, RespuestaError.conMensaje("db_no_disponible", "La base de datos no está disponible"))
        }
    }
}
