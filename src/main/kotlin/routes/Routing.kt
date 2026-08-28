package routes

import data.repository.AppAndroid.OnboardingRepository
import data.repository.admin.AdminUserRepository
import data.repository.billing.SuscripcionRepository
import data.repository.usuarios.ConsentTextRepository
import data.repository.usuarios.ConsentimientoRepository
import data.repository.usuarios.ObjetivoCarreraRepository
import data.repository.usuarios.PasswordResetRepository
import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.RecordatorioPreferenciaRepository
import data.repository.usuarios.UserRepository
import data.repository.usuarios.UsuariosOAuthRepositoryImpl
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.Database
import plugins.settings
import routes.admin.AdminUserCreateRoutes
import routes.admin.adminRoutes
import routes.auth.authRoutes
import routes.auth.deleteAccountRoute
import routes.auth.googleAuthRoutes
import routes.auth.passwordRecoveryRoutes
import routes.auth.profileRoutes
import routes.billing.billingRoutes
import routes.consent.ConsentRoutes
import routes.me.meRoutes
import routes.onboarding.onboardingRoutes
import routes.usuario.recordatorios.recordatorioRoutes
import security.AuthCtx
import security.AuthCtxKey
import security.auth.GoogleTokenVerifier
import security.billing.GooglePlayBillingService
import services.EmailService

import data.repository.sync.SyncRepository
import routes.sync.syncRoutes

/** API reducida al dominio de cuentas y administracion de usuarios. */
fun Application.configureRouting(
    adminUserRepo: AdminUserRepository,
    recoveryCodeRepo: PasswordResetRepository,
    emailService: EmailService,
    db: Database
) {
    val users = UserRepository()
    val profiles = ProfileRepository()
    val objetivos = ObjetivoCarreraRepository()
    val consentRepo = ConsentimientoRepository()
    val onboardingRepo = OnboardingRepository()
    val recordatorioRepo = RecordatorioPreferenciaRepository()
    val suscripcionRepo = SuscripcionRepository()
    val oauthRepo = UsuariosOAuthRepositoryImpl()
    val syncRepo = SyncRepository()
    val ctx: AuthCtx = attributes[AuthCtxKey]
    val s = settings()

    val billingService = GooglePlayBillingService(
        userRepo = users,
        suscripcionRepo = suscripcionRepo,
        packageName = s.googlePlayPackage,
        serviceAccountJsonBase64 = s.googlePlayServiceJsonBase64,
        useMock = s.googlePlayBillingMock
    )

    routing {
        get("/health") { call.respondText("OK") }

        authRoutes(ctx.issuer, ctx.audience, ctx.algorithm)
        googleAuthRoutes(oauthRepo, GoogleTokenVerifier(s.googleClientId))
        passwordRecoveryRoutes(recoveryCodeRepo, emailService, db, oauthRepo)
        deleteAccountRoute(users)

        meRoutes(users, profiles, objetivos)
        profileRoutes(onboardingRepo)
        onboardingRoutes(profiles, objetivos)
        recordatorioRoutes(recordatorioRepo)
        ConsentRoutes(consentRepo, ConsentTextRepository())
        billingRoutes(billingService, suscripcionRepo)
        syncRoutes(syncRepo)

        AdminUserCreateRoutes(adminUserRepo)
        adminRoutes(adminUserRepo)
    }
}
