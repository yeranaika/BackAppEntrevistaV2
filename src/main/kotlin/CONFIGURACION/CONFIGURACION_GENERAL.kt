package CONFIGURACION

import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import io.ktor.util.AttributeKey

private const val PUERTO_REDIS_POR_DEFECTO = 6379
private const val PUERTO_SMTP_POR_DEFECTO = 465
private const val TAMANO_POOL_BD_POR_DEFECTO = 10
private const val HOST_JSEARCH_POR_DEFECTO = "jsearch.p.rapidapi.com"

data class ConfiguracionBaseDatos(
    val url: String,
    val usuario: String,
    val contrasena: String,
    val tamanoMaximoPool: Int
)

data class ConfiguracionJwt(
    val emisor: String,
    val audiencia: String,
    val secreto: String
)

data class ConfiguracionGoogle(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String
)

data class ConfiguracionGooglePlay(
    val paquete: String,
    val cuentaServicioJsonBase64: String,
    val esSimulado: Boolean
)

data class ConfiguracionRedis(
    val host: String,
    val puerto: Int,
    val contrasena: String?
)

data class ConfiguracionCorreo(
    val hostSmtp: String,
    val puertoSmtp: Int,
    val usuario: String,
    val contrasena: String
)

data class ConfiguracionMercadoLaboral(
    val apiKey: String?,
    val apiHost: String
)

data class ConfiguracionLlm(
    val openAiApiKey: String,
    val anthropicApiKey: String
) {
    val tieneProveedor: Boolean get() = openAiApiKey.isNotBlank() || anthropicApiKey.isNotBlank()
}

/** Toda la configuración externa de la aplicación, leída una sola vez al arrancar. */
data class ConfiguracionGeneral(
    val baseDatos: ConfiguracionBaseDatos,
    val jwt: ConfiguracionJwt,
    val google: ConfiguracionGoogle,
    val googlePlay: ConfiguracionGooglePlay,
    val redis: ConfiguracionRedis,
    val correo: ConfiguracionCorreo,
    val mercadoLaboral: ConfiguracionMercadoLaboral,
    val llm: ConfiguracionLlm
)

private val ClaveConfiguracion = AttributeKey<ConfiguracionGeneral>("configuracion-general")

/** Devuelve la configuración; la primera llamada la carga y la deja en caché en la aplicación. */
fun Application.configuracion(): ConfiguracionGeneral =
    attributes.computeIfAbsent(ClaveConfiguracion) { cargarConfiguracion(environment) }

private class LectorConfiguracion(entorno: ApplicationEnvironment) {
    private val archivoYaml = entorno.config
    // En CI/producción puede no existir .env; ahí manda la variable de entorno del sistema.
    private val archivoEnv = dotenv { ignoreIfMissing = true }

    fun opcional(clave: String, rutaYaml: String? = null): String? =
        archivoEnv[clave]
            ?: System.getenv(clave)
            ?: rutaYaml?.let { archivoYaml.propertyOrNull(it)?.getString() }

    fun requerido(clave: String, rutaYaml: String? = null): String =
        opcional(clave, rutaYaml) ?: error("Falta configuración: $clave")

    fun entero(clave: String, porDefecto: Int): Int = opcional(clave)?.toIntOrNull() ?: porDefecto

    fun booleano(clave: String, rutaYaml: String? = null): Boolean =
        opcional(clave, rutaYaml)?.lowercase() in setOf("true", "1", "yes")
}

private fun cargarConfiguracion(entorno: ApplicationEnvironment): ConfiguracionGeneral {
    val lector = LectorConfiguracion(entorno)
    return ConfiguracionGeneral(
        baseDatos = ConfiguracionBaseDatos(
            url = lector.requerido("DB_URL", "db.url"),
            usuario = lector.requerido("DB_USER", "db.user"),
            contrasena = lector.requerido("DB_PASS", "db.pass"),
            tamanoMaximoPool = lector.entero("DB_POOL_MAXIMO", TAMANO_POOL_BD_POR_DEFECTO)
        ),
        jwt = ConfiguracionJwt(
            emisor = lector.requerido("JWT_ISSUER", "security.jwt.issuer"),
            audiencia = lector.requerido("JWT_AUDIENCE", "security.jwt.audience"),
            secreto = lector.requerido("JWT_SECRET", "security.jwt.secret")
        ),
        google = ConfiguracionGoogle(
            clientId = lector.requerido("GOOGLE_CLIENT_ID", "google.clientId"),
            clientSecret = lector.requerido("GOOGLE_CLIENT_SECRET", "google.clientSecret"),
            redirectUri = lector.requerido("GOOGLE_REDIRECT_URI", "google.redirectUri")
        ),
        googlePlay = ConfiguracionGooglePlay(
            paquete = lector.requerido("GOOGLE_PLAY_PACKAGE", "google.play.package"),
            cuentaServicioJsonBase64 = lector.requerido("GOOGLE_PLAY_SERVICE_JSON_B64", "google.play.serviceJsonB64"),
            esSimulado = lector.booleano("GOOGLE_PLAY_BILLING_MOCK", "google.play.billingMock")
        ),
        redis = ConfiguracionRedis(
            host = lector.opcional("REDIS_HOST") ?: "localhost",
            puerto = lector.entero("REDIS_PORT", PUERTO_REDIS_POR_DEFECTO),
            contrasena = lector.opcional("REDIS_PASSWORD")
        ),
        correo = ConfiguracionCorreo(
            hostSmtp = lector.opcional("SMTP_HOST") ?: "smtp.gmail.com",
            puertoSmtp = lector.entero("SMTP_PORT", PUERTO_SMTP_POR_DEFECTO),
            usuario = lector.requerido("GMAIL_USER"),
            contrasena = lector.requerido("GMAIL_APP_PASSWORD")
        ),
        mercadoLaboral = ConfiguracionMercadoLaboral(
            apiKey = lector.opcional("JSEARCH_API_KEY", "jsearch.apiKey"),
            apiHost = lector.opcional("JSEARCH_API_HOST", "jsearch.apiHost") ?: HOST_JSEARCH_POR_DEFECTO
        ),
        llm = ConfiguracionLlm(
            openAiApiKey = lector.opcional("OPENAI_API_KEY").orEmpty(),
            anthropicApiKey = lector.opcional("ANTHROPIC_API_KEY").orEmpty()
        )
    )
}
