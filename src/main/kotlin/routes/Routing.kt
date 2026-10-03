package routes

import CONFIGURACION.ContenedorDependencias
import CONTROLADORES.controladorAdminUsuario
import CONTROLADORES.controladorContrasena
import CONTROLADORES.controladorLogin
import CONTROLADORES.controladorOnboarding
import CONTROLADORES.controladorUsuario
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import routes.admin.adminAiRoutes
import routes.billing.billingRoutes
import routes.consent.ConsentRoutes
import routes.legal.legalRoutes
import routes.market.marketRoutes
import routes.skills.skillRoutes
import routes.sync.syncRoutes
import routes.usuario.recordatorios.recordatorioRoutes

/** Monta todas las rutas con las dependencias ya construidas en el contenedor. */
fun Application.configurarRutas(d: ContenedorDependencias) {
    routing {
        get("/health") { call.respondText("OK") }

        // Fases 1 y 2: capas MVC
        controladorLogin(d.servicioLogin)
        controladorUsuario(d.servicioUsuario)
        controladorContrasena(d.servicioContrasena)
        controladorOnboarding(d.servicioOnboarding)
        controladorAdminUsuario(d.servicioAdminUsuario)

        // Pendientes de migrar (ver documentacion/PLAN_REFACTORIZACION.md)
        recordatorioRoutes(d.repositorioRecordatorio)
        ConsentRoutes(d.repositorioConsentimiento, d.repositorioTextoConsentimiento)
        billingRoutes(d.servicioFacturacion, d.repositorioSuscripcion)
        syncRoutes(d.repositorioSincronizacion)
        skillRoutes(d.repositorioCargoSkill, d.cache)
        legalRoutes(d.repositorioTextoConsentimiento)
        adminAiRoutes(d.servicioGeneracionPreguntas)

        marketRoutes(
            skillMarketRepository = d.repositorioMercadoSkill,
            cargoRepository = d.repositorioCargo,
            cargoSkillGenerator = d.generadorSkillsCargo,
            skillTrendWorker = d.workerTendencias
        )
    }
}
