// src/main/kotlin/plugins/Settings.kt
package plugins

import io.ktor.server.application.*
import io.github.cdimascio.dotenv.dotenv
import io.ktor.util.AttributeKey

data class Settings(
    val dbUrl: String,
    val dbUser: String,
    val dbPass: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtSecret: String,
    val googleClientId: String,
    val googleClientSecret: String,
    val googleRedirectUri: String,
    val googlePlayPackage: String,
    val googlePlayServiceJsonBase64: String,
    val googlePlayBillingMock: Boolean,
    val jsearchApiKey: String? = null,
    val jsearchApiHost: String = "jsearch.p.rapidapi.com",
)

// Cache en attributes para no releer en cada uso
private val SettingsKey = AttributeKey<Settings>("app-settings")

// Cárgalo una vez al iniciar (llámalo en Application.module())
fun Application.configureSettings() {
    if (!attributes.contains(SettingsKey)) {
        val s = loadSettings(environment)
        attributes.put(SettingsKey, s)
        // Loguea solo lo no sensible
        log.info(
            "Settings loaded → dbUrl=${s.dbUrl}, dbUser=${s.dbUser}, " +
                "jwtIssuer=${s.jwtIssuer}, jwtAudience=${s.jwtAudience}"
        )
    }
}

// Obtén los settings desde cualquier parte (usa el cache si ya está)
fun Application.settings(): Settings =
    if (attributes.contains(SettingsKey)) attributes[SettingsKey]
    else loadSettings(environment)

// Implementación real de lectura
private fun loadSettings(envApp: ApplicationEnvironment): Settings {
    val cfg = envApp.config
    val env = dotenv {
        ignoreIfMissing = true   // en CI/Prod puedes no tener .env
    }

    fun readOrNull(key: String, path: String): String? =
        env[key]
            ?: System.getenv(key)
            ?: cfg.propertyOrNull(path)?.getString()

    fun read(key: String, path: String): String =
        readOrNull(key, path) ?: error("Falta config: $key / $path")

    fun readBool(key: String, path: String, default: Boolean = false): Boolean =
        readOrNull(key, path)?.lowercase()?.let { value ->
            value == "true" || value == "1" || value == "yes"
        } ?: default

    return Settings(
        dbUrl       = read("DB_URL", "db.url"),
        dbUser      = read("DB_USER", "db.user"),
        dbPass      = read("DB_PASS", "db.pass"),
        jwtIssuer   = read("JWT_ISSUER", "security.jwt.issuer"),
        jwtAudience = read("JWT_AUDIENCE", "security.jwt.audience"),
        jwtSecret   = read("JWT_SECRET", "security.jwt.secret"),
        googleClientId    = read("GOOGLE_CLIENT_ID", "google.clientId"),
        googleClientSecret= read("GOOGLE_CLIENT_SECRET", "google.clientSecret"),
        googleRedirectUri = read("GOOGLE_REDIRECT_URI", "google.redirectUri"),
        googlePlayPackage = read("GOOGLE_PLAY_PACKAGE", "google.play.package"),
        googlePlayServiceJsonBase64 = read("GOOGLE_PLAY_SERVICE_JSON_B64", "google.play.serviceJsonB64"),
        googlePlayBillingMock = readBool("GOOGLE_PLAY_BILLING_MOCK", "google.play.billingMock", default = false),
        jsearchApiKey = readOrNull("JSEARCH_API_KEY", "jsearch.apiKey"),
        jsearchApiHost = readOrNull("JSEARCH_API_HOST", "jsearch.apiHost") ?: "jsearch.p.rapidapi.com",
    )
}
