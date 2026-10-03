package MIDDLEWARES

import ESQUEMAS.RespuestaError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import security.isAdmin

private val SoloAdmin = createRouteScopedPlugin("SoloAdmin") {
    on(AuthenticationChecked) { call ->
        // Sin principal, authenticate("auth-jwt") ya responde 401; aquí solo se decide el 403.
        val principal = call.principal<JWTPrincipal>() ?: return@on
        if (!principal.isAdmin()) {
            call.respond(
                HttpStatusCode.Forbidden,
                RespuestaError("admin_required", "Se requiere rol de administrador")
            )
        }
    }
}

/**
 * Nodo propio para las rutas admin: Ktor fusiona los nodos authenticate("auth-jwt") de un mismo
 * padre, así que instalar el plugin ahí lo aplicaría también a rutas hermanas de usuario normal.
 */
private object SelectorSoloAdmin : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(solo admin)"
}

private val PluginInstalado = AttributeKey<Unit>("solo-admin-instalado")

/** Agrupa rutas que exigen JWT válido con rol admin (401 sin token, 403 sin rol). */
fun Route.soloAdmin(rutas: Route.() -> Unit) {
    authenticate("auth-jwt") {
        val rutaAdmin = createChild(SelectorSoloAdmin)
        if (!rutaAdmin.attributes.contains(PluginInstalado)) {
            rutaAdmin.install(SoloAdmin)
            rutaAdmin.attributes.put(PluginInstalado, Unit)
        }
        rutaAdmin.rutas()
    }
}
