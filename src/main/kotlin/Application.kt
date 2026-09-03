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
import data.repository.usuarios.PasswordResetRepository
import data.repository.market.CargoRepository
import data.repository.market.SkillMarketRepository
import data.repository.skills.CargoSkillRepository
import services.cache.RedisCacheService
import services.market.CargoSkillGeneratorService
import services.market.JobMarketClient
import services.market.SkillTrendWorker

import services.EmailService
import io.github.cdimascio.dotenv.dotenv

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    install(IgnoreTrailingSlash)
    // Orden importante
    configureCORS()
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureSecurity()   // esto inicializa AuthCtx

    val dotenv = dotenv {
        ignoreIfMissing = true
    }

    // --- crea repos (UNA sola vez) y pásalos al routing ---
    val db = DatabaseFactory.db
    val adminUserRepo = AdminUserRepository(db)
    val skillMarketRepo = SkillMarketRepository(db)
    val cargoRepo = CargoRepository(db)
    val cargoSkillRepo = CargoSkillRepository(db)

    val recoveryCodeRepo = PasswordResetRepository()

    // Servicio de Caché Redis (Cache-Aside)
    val redisCacheService = RedisCacheService(
        host = dotenv["REDIS_HOST"] ?: "localhost",
        port = dotenv["REDIS_PORT"]?.toIntOrNull() ?: 6379,
        password = dotenv["REDIS_PASSWORD"]
    )

    // Configurar EmailService con variables de entorno
    val emailService = EmailService(
        smtpHost = dotenv["SMTP_HOST"] ?: "smtp.gmail.com",
        smtpPort = dotenv["SMTP_PORT"]?.toIntOrNull() ?: 465,
        username = dotenv["GMAIL_USER"] ?: throw RuntimeException("GMAIL_USER no configurado"),
        password = dotenv["GMAIL_APP_PASSWORD"] ?: throw RuntimeException("GMAIL_APP_PASSWORD no configurado"),
        fromEmail = dotenv["GMAIL_USER"] ?: throw RuntimeException("GMAIL_USER no configurado")
    )

    // Cliente de ofertas y generador de skills por carrera
    val jobMarketClient = JobMarketClient(
        rapidApiKey = dotenv["JSEARCH_API_KEY"],
        rapidApiHost = dotenv["JSEARCH_API_HOST"] ?: "jsearch.p.rapidapi.com"
    )

    val cargoSkillGenerator = CargoSkillGeneratorService(
        cargoRepository = cargoRepo,
        skillMarketRepository = skillMarketRepo,
        jobMarketClient = jobMarketClient
    )

    // Worker de actualización de tendencias y skills del mercado
    val skillTrendWorker = SkillTrendWorker(
        repository = skillMarketRepo,
        jobMarketClient = jobMarketClient,
        cargoSkillGenerator = cargoSkillGenerator
    )
    skillTrendWorker.start()

    monitor.subscribe(ApplicationStopped) {
        skillTrendWorker.stop()
        jobMarketClient.close()
        redisCacheService.close()
    }

    // Configurar routing con todos los repositorios y servicios
    configureRouting(
        adminUserRepo = adminUserRepo,
        recoveryCodeRepo = recoveryCodeRepo,
        emailService = emailService,
        db = db,
        skillMarketRepo = skillMarketRepo,
        cargoRepo = cargoRepo,
        cargoSkillRepo = cargoSkillRepo,
        redisCacheService = redisCacheService,
        cargoSkillGenerator = cargoSkillGenerator,
        skillTrendWorker = skillTrendWorker
    )

    configureMonitoring()
}
