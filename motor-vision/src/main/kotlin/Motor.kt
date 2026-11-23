import CONFIGURACION.CargadorModeloDivlara
import CONFIGURACION.DIRECTORIO_MODELOS_DIVLARA
import CONFIGURACION.VERSION_MODELO_DIVLARA
import CONFIGURACION.configurarErroresMotor
import CONFIGURACION.configurarSerializacionMotor
import CONTROLADORES.controladorMotor
import SERVICIOS.ServicioVision
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.routing.routing
import java.nio.file.Path

/**
 * Motor de inferencia de visión (T3.11): proceso aparte del backend. Carga `<MODELOS_DIRECTORIO>/<MODELO_VERSION>/`
 * (por defecto `models/divlara-e14-mlp`) una sola vez y atiende `POST /predict`. Cambiar de modelo o de proveedor es
 * redesplegar este proceso (o apuntar MOTOR_ENDPOINT del backend a otro servicio con el mismo contrato).
 */
fun Application.moduloMotor() {
    val directorio = System.getenv("MODELOS_DIRECTORIO")?.takeIf { it.isNotBlank() } ?: DIRECTORIO_MODELOS_DIVLARA
    val version = System.getenv("MODELO_VERSION")?.takeIf { it.isNotBlank() } ?: VERSION_MODELO_DIVLARA
    val cargador = CargadorModeloDivlara(version = version, directorioBase = Path.of(directorio))
    val estado = cargador.cargarSiDisponible()
    log.info("Motor de visión: modelo {} -> {}", version, estado.estado)
    monitor.subscribe(ApplicationStopped) { cargador.close() }

    configurarSerializacionMotor()
    configurarErroresMotor()
    routing { controladorMotor(ServicioVision(cargador), cargador) }
}
