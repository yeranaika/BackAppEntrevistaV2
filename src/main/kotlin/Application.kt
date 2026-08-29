package com.example

import io.ktor.server.application.*
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import com.example.configureMonitoring

import plugins.configureSerialization
import plugins.configureStatusPages
import plugins.configureDatabase
import plugins.configureCORS
import plugins.DatabaseFactory

import security.configureSecurity
import routes.configureRouting

import data.repository.admin.AdminUserRepository
// ❌ Antes:
// import data.repository.auth.RecoveryCodeRepository
// ✅ Ahora:
import data.repository.usuarios.PasswordResetRepository

import services.EmailService
import io.github.cdimascio.dotenv.dotenv

import data.repository.market.SkillMarketRepository
import services.market.JobMarketClient
import services.market.SkillTrendWorker

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    install(IgnoreTrailingSlash)
    // Orden importante
    configureCORS()
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureSecurity()   // esto inicializa AuthCtx

    // --- crea repos (UNA sola vez) y pásalos al routing ---
    val db = DatabaseFactory.db
    val adminUserRepo = AdminUserRepository(db)
    val skillMarketRepo = SkillMarketRepository(db)

    // ❌ Antes:
    // val recoveryCodeRepo = RecoveryCodeRepository(db)
    // ✅ Ahora usamos el repo nuevo de reset de contraseña:
    val recoveryCodeRepo = PasswordResetRepository()

    // Configurar EmailService con variables de entorno
    val dotenv = dotenv {
        ignoreIfMissing = true
    }

    val emailService = EmailService(
        smtpHost = dotenv["SMTP_HOST"] ?: "smtp.gmail.com",
        smtpPort = dotenv["SMTP_PORT"]?.toIntOrNull() ?: 465,
        username = dotenv["GMAIL_USER"] ?: throw RuntimeException("GMAIL_USER no configurado"),
        password = dotenv["GMAIL_APP_PASSWORD"] ?: throw RuntimeException("GMAIL_APP_PASSWORD no configurado"),
        fromEmail = dotenv["GMAIL_USER"] ?: throw RuntimeException("GMAIL_USER no configurado")
    )

    // Worker de actualización de tendencias y skills del mercado
    val jobMarketClient = JobMarketClient(
        rapidApiKey = dotenv["JSEARCH_API_KEY"],
        rapidApiHost = dotenv["JSEARCH_API_HOST"] ?: "jsearch.p.rapidapi.com"
    )
    val skillTrendWorker = SkillTrendWorker(
        repository = skillMarketRepo,
        jobMarketClient = jobMarketClient
    )
    skillTrendWorker.start()

    monitor.subscribe(ApplicationStopped) {
        skillTrendWorker.stop()
        jobMarketClient.close()
    }

    // 👇 ahora configureRouting recibe PasswordResetRepository y SkillTrendWorker
    configureRouting(
        adminUserRepo = adminUserRepo,
        recoveryCodeRepo = recoveryCodeRepo,
        emailService = emailService,
        db = db,
        skillMarketRepo = skillMarketRepo,
        skillTrendWorker = skillTrendWorker
    )

    configureMonitoring()
}
