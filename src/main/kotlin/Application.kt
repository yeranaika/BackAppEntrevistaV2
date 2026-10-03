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
import MIDDLEWARES.configurarSeguridad
import routes.configurarRutas

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val configuracion = configuracion()

    install(IgnoreTrailingSlash)
    configurarCors()
    configurarSerializacion()
    configurarErrores()
    val db = configurarBaseDatos(configuracion.baseDatos)
    configurarSeguridad()

    val dependencias = ContenedorDependencias(configuracion, db)
    dependencias.iniciarTareasSegundoPlano()
    monitor.subscribe(ApplicationStopped) { dependencias.close() }

    if (!configuracion.llm.tieneProveedor) {
        log.warn("OPENAI_API_KEY y ANTHROPIC_API_KEY no configurados: generate-ai responderá 503 provider_not_configured.")
    }

    configurarRutas(dependencias)
    configurarMonitoreo()
}
