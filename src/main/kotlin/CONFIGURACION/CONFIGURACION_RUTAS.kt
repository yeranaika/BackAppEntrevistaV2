package CONFIGURACION

import CONTROLADORES.controladorAdminUsuario
import CONTROLADORES.controladorConsentimiento
import CONTROLADORES.controladorContrasena
import CONTROLADORES.controladorLogin
import CONTROLADORES.controladorMercado
import CONTROLADORES.controladorOnboarding
import CONTROLADORES.controladorPregunta
import CONTROLADORES.controladorRecordatorio
import CONTROLADORES.controladorSalud
import CONTROLADORES.controladorSuscripcion
import CONTROLADORES.controladorUsuario
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import routes.sync.syncRoutes

/** Monta todas las rutas con las dependencias ya construidas en el contenedor. */
fun Application.configurarRutas(d: ContenedorDependencias) {
    routing {
        controladorSalud()

        // Cuentas y sesión
        controladorLogin(d.servicioLogin)
        controladorUsuario(d.servicioUsuario)
        controladorContrasena(d.servicioContrasena)
        controladorOnboarding(d.servicioOnboarding)
        controladorAdminUsuario(d.servicioAdminUsuario)
        controladorConsentimiento(d.servicioConsentimiento, d.documentosLegales)
        controladorRecordatorio(d.servicioRecordatorio)
        controladorSuscripcion(d.servicioSuscripcion)

        // Banco de preguntas e IA
        controladorPregunta(d.servicioPregunta, d.servicioGeneracionPregunta)

        // Mercado laboral: cargos, skills y tendencias
        controladorMercado(d.servicioMercado, d.servicioTendencias)

        // Pendiente de migrar con la práctica (Fase 6)
        syncRoutes(d.repositorioSincronizacion)
    }
}
