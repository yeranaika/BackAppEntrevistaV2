package CONFIGURACION

import com.auth0.jwt.algorithms.Algorithm
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import io.ktor.util.AttributeKey
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val PUERTO_REDIS_POR_DEFECTO = 6379
private const val PUERTO_SMTP_POR_DEFECTO = 465
private const val TAMANO_POOL_BD_POR_DEFECTO = 10
private const val REGISTROS_POR_IP_POR_DEFECTO = 30
private const val RECUPERACIONES_POR_IP_POR_DEFECTO = 5
private const val GENERACIONES_IA_POR_IP_POR_DEFECTO = 30
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
) {
    /** Mismo algoritmo para firmar (ServicioToken) y verificar (MIDDLEWARE_AUTENTICACION). */
    val algoritmo: Algorithm by lazy { Algorithm.HMAC512(secreto) }
}

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
    val anthropicApiKey: String,
    /** Tope mensual de evaluaciones con IA por usuario premium; 0 = sin IA (D17). */
    val evaluacionesIaMensuales: Int = EVALUACIONES_IA_MENSUALES_POR_DEFECTO,
    /** Tope mensual (USD estimados, UTC) de la generación de preguntas con IA; 0 = desactivada (D23). */
    val gastoGeneracionIaMensualUsd: Double = GASTO_GENERACION_IA_MENSUAL_USD_POR_DEFECTO
) {
    val tieneProveedor: Boolean get() = openAiApiKey.isNotBlank() || anthropicApiKey.isNotBlank()
}

/** Solicitudes permitidas por IP en cada ventana (ver CONFIGURACION_LIMITE_SOLICITUDES). */
data class ConfiguracionLimites(
    val registrosPorIp: Int,
    val recuperacionesPorIp: Int,
    /**
     * Cabecera en la que el proxy de confianza escribe la IP del cliente (en Railway: X-Real-IP). null = la conexión
     * TCP directa. Solo se activa tras comprobar que el proxy la sobrescribe (si no, el cliente podría falsificarla).
     */
    val cabeceraIpCliente: String? = null,
    /** Generaciones de preguntas con IA por IP en cada ventana (anti-ráfaga; el gasto lo frena el tope mensual, D23). */
    val generacionesIaPorIp: Int = GENERACIONES_IA_POR_IP_POR_DEFECTO
) {
    init {
        cabeceraIpCliente?.let {
            require(Regex("^[A-Za-z0-9-]+$").matches(it)) { "CABECERA_IP_CLIENTE inválida: $it" }
            require(!it.equals("X-Forwarded-For", ignoreCase = true)) {
                "CABECERA_IP_CLIENTE no admite X-Forwarded-For (el cliente controla su primer valor): usa X-Real-IP"
            }
        }
    }
}

/** Apagado ordenado (D20): cuánto se espera a los reportes y correos en curso antes de cancelarlos. */
data class ConfiguracionApagado(val esperaTareas: Duration = ESPERA_APAGADO_SEGUNDOS_POR_DEFECTO.seconds)

/** Toda la configuración externa de la aplicación, leída una sola vez al arrancar. */
data class ConfiguracionGeneral(
    val baseDatos: ConfiguracionBaseDatos,
    val jwt: ConfiguracionJwt,
    val google: ConfiguracionGoogle,
    val googlePlay: ConfiguracionGooglePlay,
    val redis: ConfiguracionRedis,
    val correo: ConfiguracionCorreo,
    val mercadoLaboral: ConfiguracionMercadoLaboral,
    val llm: ConfiguracionLlm,
    val limites: ConfiguracionLimites,
    val apagado: ConfiguracionApagado = ConfiguracionApagado()
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

/**
 * Tope mensual de la generación con IA (D23). Sin definir: el valor por defecto. Acepta coma decimal ("0,5"). Un valor
 * definido pero ilegible, negativo o no finito se toma como 0 (generación desactivada) con un aviso: ante la duda no
 * se gasta en IA, y no se cae en silencio a un tope más alto que el que se quiso poner.
 */
internal fun leerTopeGastoUsd(valor: String?): Double {
    val texto = valor?.trim()?.takeIf { it.isNotEmpty() } ?: return GASTO_GENERACION_IA_MENSUAL_USD_POR_DEFECTO
    val numero = texto.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    if (numero == null) {
        org.slf4j.LoggerFactory.getLogger("Configuracion")
            .warn("LIMITE_GASTO_GENERACION_IA_MENSUAL_USD='{}' no es un monto válido: la generación con IA queda desactivada", texto)
    }
    return numero ?: 0.0
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
            anthropicApiKey = lector.opcional("ANTHROPIC_API_KEY").orEmpty(),
            // Un valor negativo se toma como 0: ante la duda no se gasta en IA.
            evaluacionesIaMensuales = lector.entero("LIMITE_EVALUACIONES_IA_MENSUAL", EVALUACIONES_IA_MENSUALES_POR_DEFECTO).coerceAtLeast(0),
            gastoGeneracionIaMensualUsd = leerTopeGastoUsd(lector.opcional("LIMITE_GASTO_GENERACION_IA_MENSUAL_USD"))
        ),
        limites = ConfiguracionLimites(
            registrosPorIp = lector.entero("LIMITE_REGISTROS_POR_IP", REGISTROS_POR_IP_POR_DEFECTO),
            recuperacionesPorIp = lector.entero("LIMITE_RECUPERACIONES_POR_IP", RECUPERACIONES_POR_IP_POR_DEFECTO),
            cabeceraIpCliente = lector.opcional("CABECERA_IP_CLIENTE")?.trim()?.takeIf { it.isNotEmpty() },
            generacionesIaPorIp = lector.entero("LIMITE_GENERACIONES_IA_POR_IP", GENERACIONES_IA_POR_IP_POR_DEFECTO).coerceAtLeast(1)
        ),
        apagado = ConfiguracionApagado(
            // Entre 0 y 10 minutos: tiene que caber en el tiempo de drenado de Railway (RAILWAY_DEPLOYMENT_DRAINING_SECONDS).
            esperaTareas = lector.entero("APAGADO_ESPERA_TAREAS_SEGUNDOS", ESPERA_APAGADO_SEGUNDOS_POR_DEFECTO).coerceIn(0, 600).seconds
        )
    )
}
