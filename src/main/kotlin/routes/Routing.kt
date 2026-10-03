package routes

import CONFIGURACION.ContenedorDependencias
import controllers.authController
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import MIDDLEWARES.AuthCtx
import MIDDLEWARES.AuthCtxKey
import routes.admin.AdminUserCreateRoutes
import routes.admin.adminAiRoutes
import routes.admin.adminRoutes
import routes.auth.authRoutes
import routes.auth.deleteAccountRoute
import routes.auth.passwordRecoveryRoutes
import routes.auth.profileRoutes
import routes.billing.billingRoutes
import routes.consent.ConsentRoutes
import routes.legal.legalRoutes
import routes.market.marketRoutes
import routes.me.meRoutes
import routes.onboarding.onboardingRoutes
import routes.skills.skillRoutes
import routes.sync.syncRoutes
import routes.usuario.recordatorios.recordatorioRoutes

/** Monta todas las rutas con las dependencias ya construidas en el contenedor. */
fun Application.configurarRutas(d: ContenedorDependencias) {
    val ctx: AuthCtx = attributes[AuthCtxKey]

    routing {
        get("/health") { call.respondText("OK") }

        authController(d.servicioAuth, ctx.issuer, ctx.audience, ctx.algorithm)
        authRoutes(d.repositorioRefreshToken, ctx.issuer, ctx.audience, ctx.algorithm)
        passwordRecoveryRoutes(d.repositorioRecuperacion, d.servicioCorreo, d.repositorioUsuario, d.repositorioOAuth)
        deleteAccountRoute(d.repositorioUsuario)

        meRoutes(d.repositorioUsuario, d.repositorioPerfil, d.repositorioObjetivo)
        profileRoutes(d.repositorioOnboarding)
        onboardingRoutes(d.repositorioPerfil, d.repositorioObjetivo)
        recordatorioRoutes(d.repositorioRecordatorio)
        ConsentRoutes(d.repositorioConsentimiento, d.repositorioTextoConsentimiento)
        billingRoutes(d.servicioFacturacion, d.repositorioSuscripcion)
        syncRoutes(d.repositorioSincronizacion)

        AdminUserCreateRoutes(d.repositorioAdminUsuario)
        adminRoutes(d.repositorioAdminUsuario)
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
