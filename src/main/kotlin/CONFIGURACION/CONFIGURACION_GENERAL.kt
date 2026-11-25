package CONFIGURACION

import INTEGRACIONES.CREDITOS_TOKEN_PLAN_LITE
import INTEGRACIONES.URL_MIMO_POR_DEFECTO
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
private const val MOTOR_ENDPOINT_POR_DEFECTO = "http://localhost:8094/predict"
private const val MOTOR_TIEMPO_MAXIMO_MS_POR_DEFECTO = 3_000L
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
    /** Xiaomi MiMo (D30). Vacía = sin MiMo. */
    val mimoApiKey: String = "",
    /** Endpoint de Chat Completions de MiMo (MIMO_BASE_URL); por defecto, el de pago por uso. */
    val mimoUrl: String = URL_MIMO_POR_DEFECTO,
    /** Tope mensual de evaluaciones con IA por usuario premium; 0 = sin IA (D17). */
    val evaluacionesIaMensuales: Int = EVALUACIONES_IA_MENSUALES_POR_DEFECTO,
    /** Tope mensual (USD estimados, UTC) de la generación de preguntas con IA; 0 = desactivada (D23). */
    val gastoGeneracionIaMensualUsd: Double = GASTO_GENERACION_IA_MENSUAL_USD_POR_DEFECTO,
    /** Credits por periodo del Token Plan de MiMo (D31); 0 = sin Token Plan (clave de pago por uso o sin MiMo). */
    val mimoTokenPlanCreditos: Long = 0,
    /** Día del mes (1–28, UTC) en que se renueva el Token Plan. */
    val mimoTokenPlanDiaRenovacion: Int = 1
) {
    val tieneProveedor: Boolean get() = openAiApiKey.isNotBlank() || anthropicApiKey.isNotBlank() || mimoApiKey.isNotBlank()
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

/**
 * Motor de inferencia de visión (T3.11): proceso aparte que carga el modelo. Es solo la URL de `POST /predict`: apuntarla a
 * otro motor (o a un proveedor externo con el mismo contrato) cambia el modelo sin tocar el backend.
 */
data class ConfiguracionMotorVision(
    val endpoint: String = MOTOR_ENDPOINT_POR_DEFECTO,
    /** Tiempo máximo de cada intento (hay 1 reintento). */
    val tiempoMaximoMs: Long = MOTOR_TIEMPO_MAXIMO_MS_POR_DEFECTO
) {
    init {
        require(Regex("^https?://[^\\s/]+(/.*)?$").matches(endpoint)) {
            "MOTOR_ENDPOINT inválido (debe ser http(s)://host[:puerto]/ruta)"
        }
        require(tiempoMaximoMs > 0) { "MOTOR_TIMEOUT_MS debe ser mayor a cero" }
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
    val apagado: ConfiguracionApagado = ConfiguracionApagado(),
    val motorVision: ConfiguracionMotorVision = ConfiguracionMotorVision(),
    /**
     * HABILITAR_PRUEBAS_VIDEO=true monta `POST /pruebas/video` y `GET /pruebas` (calibración del modelo de visión, sin
     * sesión: guardan .mp4 en el disco del servidor). Apagado por defecto y nunca en Railway (D39).
     */
    val pruebasVideoHabilitadas: Boolean = false,
    /**
     * TAREAS_SEGUNDO_PLANO=false no arranca las tareas periódicas (sincronización de mercado, rescate de reportes y purga
     * de consentimientos): para un backend local conectado a la BD compartida de Railway. Nunca en Railway (D43).
     */
    val tareasSegundoPlanoActivas: Boolean = true
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

/**
 * Credits del Token Plan de MiMo (D31). Sin definir: el plan Lite si la clave es de Token Plan (empieza con "tp-"), si no
 * 0. Acepta separadores de miles ("4.100.000.000", "4,100,000,000", "4_100_000_000"). Un valor ilegible o negativo se
 * toma como 0 con un aviso: sin Token Plan, la generación sigue frenada por el tope en USD (D23).
 */
internal fun leerCreditosTokenPlan(valor: String?, claveMimo: String): Long {
    val texto = valor?.trim()?.takeIf { it.isNotEmpty() }
        ?: return if (claveMimo.trim().startsWith("tp-")) CREDITOS_TOKEN_PLAN_LITE else 0
    val numero = texto.replace(Regex("[.,_\\s]"), "").toLongOrNull()?.takeIf { it >= 0 }
    if (numero == null) {
        org.slf4j.LoggerFactory.getLogger("Configuracion")
            .warn("MIMO_TOKEN_PLAN_CREDITOS='{}' no es un número válido: se ignora el Token Plan", texto)
    }
    return numero ?: 0
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
            mimoApiKey = lector.opcional("MIMO_API_KEY").orEmpty(),
            mimoUrl = urlChatMimo(lector.opcional("MIMO_BASE_URL")),
            // Un valor negativo se toma como 0: ante la duda no se gasta en IA.
            evaluacionesIaMensuales = lector.entero("LIMITE_EVALUACIONES_IA_MENSUAL", EVALUACIONES_IA_MENSUALES_POR_DEFECTO).coerceAtLeast(0),
            gastoGeneracionIaMensualUsd = leerTopeGastoUsd(lector.opcional("LIMITE_GASTO_GENERACION_IA_MENSUAL_USD")),
            mimoTokenPlanCreditos = leerCreditosTokenPlan(lector.opcional("MIMO_TOKEN_PLAN_CREDITOS"), lector.opcional("MIMO_API_KEY").orEmpty()),
            mimoTokenPlanDiaRenovacion = lector.entero("MIMO_TOKEN_PLAN_DIA_RENOVACION", 1).coerceIn(1, 28)
        ),
        limites = ConfiguracionLimites(
            registrosPorIp = lector.entero("LIMITE_REGISTROS_POR_IP", REGISTROS_POR_IP_POR_DEFECTO),
            recuperacionesPorIp = lector.entero("LIMITE_RECUPERACIONES_POR_IP", RECUPERACIONES_POR_IP_POR_DEFECTO),
            cabeceraIpCliente = lector.opcional("CABECERA_IP_CLIENTE")?.trim()?.takeIf { it.isNotEmpty() },
            generacionesIaPorIp = lector.entero("LIMITE_GENERACIONES_IA_POR_IP", GENERACIONES_IA_POR_IP_POR_DEFECTO).coerceAtLeast(1)
        ),
        motorVision = ConfiguracionMotorVision(
            endpoint = lector.opcional("MOTOR_ENDPOINT", "vision.motorEndpoint")?.trim()?.takeIf { it.isNotEmpty() } ?: MOTOR_ENDPOINT_POR_DEFECTO,
            tiempoMaximoMs = (lector.opcional("MOTOR_TIMEOUT_MS")?.toLongOrNull() ?: MOTOR_TIEMPO_MAXIMO_MS_POR_DEFECTO).coerceIn(100L, 30_000L)
        ),
        apagado = ConfiguracionApagado(
            // Entre 0 y 10 minutos: tiene que caber en el tiempo de drenado de Railway (RAILWAY_DEPLOYMENT_DRAINING_SECONDS).
            esperaTareas = lector.entero("APAGADO_ESPERA_TAREAS_SEGUNDOS", ESPERA_APAGADO_SEGUNDOS_POR_DEFECTO).coerceIn(0, 600).seconds
        ),
        pruebasVideoHabilitadas = pruebasVideoHabilitadas(lector.opcional("HABILITAR_PRUEBAS_VIDEO"), System::getenv),
        tareasSegundoPlanoActivas = tareasSegundoPlanoActivas(lector.opcional("TAREAS_SEGUNDO_PLANO"), System::getenv)
    )
}

/**
 * TAREAS_SEGUNDO_PLANO (D43): encendidas salvo con el literal "false". Sirve para un backend local que usa la BD de
 * producción (el mismo banco de preguntas para todo el equipo) sin que ese proceso rescate reportes de usuarios reales,
 * llame a las APIs de empleo ni purgue consentimientos. Dentro de Railway el servidor no arranca con "false": sin rescate
 * los reportes cortados por un apagado quedan en 'generando' y sin purga se incumple el plazo legal (D38). Como en D39,
 * [entorno] lee las variables del sistema directamente, sin el .env.
 */
internal fun tareasSegundoPlanoActivas(valor: String?, entorno: (String) -> String?): Boolean {
    val apagadas = valor?.trim()?.lowercase() == "false"
    val enRailway = entorno("RAILWAY_PROJECT_ID") != null || entorno("RAILWAY_ENVIRONMENT_ID") != null
    check(!(apagadas && enRailway)) {
        "TAREAS_SEGUNDO_PLANO=false no se permite en Railway: sin ellas no se rescatan reportes ni se purgan consentimientos (D43)"
    }
    return !apagadas
}

/**
 * HABILITAR_PRUEBAS_VIDEO (D39): solo el literal "true" monta `/pruebas/video` y `/pruebas`. Dentro de Railway (que
 * define RAILWAY_PROJECT_ID y RAILWAY_ENVIRONMENT_ID en todo despliegue) el servidor no arranca con la variable activa:
 * esas rutas no tienen sesión y guardan video en disco. [entorno] lee las variables del sistema directamente, sin el
 * .env, para que un archivo no pueda ocultar que se está en Railway.
 */
internal fun pruebasVideoHabilitadas(valor: String?, entorno: (String) -> String?): Boolean {
    val activa = valor?.trim()?.lowercase() == "true"
    val enRailway = entorno("RAILWAY_PROJECT_ID") != null || entorno("RAILWAY_ENVIRONMENT_ID") != null
    check(!(activa && enRailway)) {
        "HABILITAR_PRUEBAS_VIDEO=true no se permite en Railway: /pruebas/video no tiene sesión y guarda video en disco (D39)"
    }
    return activa
}

/**
 * MIMO_BASE_URL acepta la base que muestra la consola de MiMo (…/v1) o la URL completa de chat/completions.
 * Vacía o ausente: el endpoint de pago por uso. Solo https, para no mandar la clave en texto plano: un valor que no
 * empieza con https:// se ignora con un aviso (como LIMITE_GASTO_GENERACION_IA_MENSUAL_USD, nunca se cae el arranque).
 */
internal fun urlChatMimo(valor: String?): String {
    val base = valor?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return URL_MIMO_POR_DEFECTO
    if (!base.startsWith("https://")) {
        org.slf4j.LoggerFactory.getLogger("Configuracion")
            .warn("MIMO_BASE_URL no empieza con https://: se usa el endpoint de pago por uso de MiMo")
        return URL_MIMO_POR_DEFECTO
    }
    return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
}
