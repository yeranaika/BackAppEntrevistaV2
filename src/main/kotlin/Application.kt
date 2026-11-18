import CONFIGURACION.ContenedorDependencias
import CONFIGURACION.configuracion
import CONFIGURACION.configurarBaseDatos
import CONFIGURACION.configurarCors
import CONFIGURACION.configurarErrores
import CONFIGURACION.configurarMonitoreo
import CONFIGURACION.configurarSerializacion
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.IgnoreTrailingSlash
import kotlinx.coroutines.runBlocking
import MIDDLEWARES.configurarSeguridad
import CONFIGURACION.configurarLimiteSolicitudes
import CONFIGURACION.configurarRutas
import CONFIGURACION.crearEstadoFreemiumReadiness
import CONFIGURACION.configurarCalentamientoFreemium

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val configuracion = configuracion()

    install(IgnoreTrailingSlash)
    configurarCors()
    configurarSerializacion()
    configurarErrores()
    configurarBaseDatos(configuracion.baseDatos)
    configurarSeguridad()

    val dependencias = ContenedorDependencias(configuracion)
    // Después de crear la caché (el contador se comparte entre instancias por Redis) y antes de las rutas.
    configurarLimiteSolicitudes(configuracion.limites, dependencias.cache)
    dependencias.iniciarTareasSegundoPlano()
    monitor.subscribe(ApplicationStopped) { dependencias.close() }
    // Apagado ordenado (D20). ApplicationStopping se emite una sola vez, con Netty ya sin aceptar solicitudes y ANTES de
    // ApplicationStopped, donde se cierran el pool de la BD y Redis: aquí se esperan los reportes y correos en curso.
    // (Nunca en ApplicationStopPreparing: se emite dos veces y el socket sigue abierto.) Verificado en Ktor 3.3.1.
    monitor.subscribe(ApplicationStopping) { runBlocking { dependencias.detenerTareas(configuracion.apagado.esperaTareas) } }

    if (!configuracion.llm.tieneProveedor) {
        log.warn("OPENAI_API_KEY y ANTHROPIC_API_KEY no configurados: generate-ai responderá 503 provider_not_configured.")
    }

    val disponibilidad = crearEstadoFreemiumReadiness()
    configurarRutas(dependencias, disponibilidad)
    configurarCalentamientoFreemium(configuracion.jwt, disponibilidad)
    configurarMonitoreo()
}
