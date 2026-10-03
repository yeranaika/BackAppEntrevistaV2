package SERVICIOS

import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudCrearCodigo
import INTEGRACIONES.VerificadorCompraGoogle
import MODELOS.CodigoSuscripcion
import MODELOS.ESTADO_SUSCRIPCION_ACTIVA
import MODELOS.NuevoCodigo
import MODELOS.PLAN_GRATUITO
import MODELOS.RepositorioSuscripcion
import MODELOS.Suscripcion
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.UUID

private val TIPOS_LICENCIA = setOf("PROM", "INST", "GOOG")
private const val CARACTERES_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val LARGO_PARTE_ALEATORIA = 8
private const val LARGO_MAXIMO_ETIQUETA = 80

/** Estado que ve la app: premium si hay un plan pagado, activo y sin vencer. */
data class EstadoSuscripcion(val esPremium: Boolean, val vigente: Suscripcion?)

class ServicioSuscripcion(
    private val repositorio: RepositorioSuscripcion,
    private val verificadorGoogle: VerificadorCompraGoogle,
    private val reloj: Clock = Clock.systemUTC()
) {
    private val generadorAleatorio = SecureRandom()

    /**
     * Considera todas las suscripciones del usuario: si alguna da premium se informa esa (la que vence más tarde).
     * Antes solo se miraba la más reciente y podía ocultar otra activa.
     */
    suspend fun estado(usuarioId: UUID): EstadoSuscripcion {
        val suscripciones = repositorio.listarDeUsuario(usuarioId)
        val premium = suscripciones.filter { esPremium(it) }.maxByOrNull { it.expiracion ?: Instant.MAX }
        return EstadoSuscripcion(esPremium = premium != null, vigente = premium ?: suscripciones.firstOrNull())
    }

    suspend fun verificarCompraGoogle(usuarioId: UUID, productoId: String, tokenCompra: String) {
        if (productoId.isBlank() || tokenCompra.isBlank()) throw ErrorValidacion("compra_incompleta", "Faltan el producto o el token de compra")
        val compra = verificadorGoogle.verificar(productoId.trim(), tokenCompra.trim())
        if (!compra.esValida || !compra.estaActiva) throw ErrorValidacion("compra_invalida", "Compra no válida o no activa")
        // Se guarda solo el hash: el token es una credencial de pago.
        repositorio.registrarCompraGoogle(usuarioId, productoId.trim(), compra.expiracion, hashear(tokenCompra.trim()))
    }

    suspend fun canjearCodigo(usuarioId: UUID, codigo: String): EstadoSuscripcion {
        val normalizado = codigo.trim().uppercase()
        if (normalizado.isEmpty() || !repositorio.canjearCodigo(usuarioId, normalizado, reloj.instant())) {
            throw ErrorValidacion("codigo_invalido_o_expirado", "El código no existe, venció o ya se usó")
        }
        return estado(usuarioId)
    }

    suspend fun crearCodigo(solicitud: SolicitudCrearCodigo): CodigoSuscripcion {
        val tipo = solicitud.tipoLicencia.trim().uppercase()
        if (tipo !in TIPOS_LICENCIA) throw ErrorValidacion("licencia_invalida", "licenseType debe ser PROM, INST o GOOG")
        if (solicitud.dias <= 0 && solicitud.expiracion == null) {
            throw ErrorValidacion("duracion_requerida", "Define days > 0 o expires_at")
        }
        if (solicitud.dias < 0) throw ErrorValidacion("dias_invalidos", "days no puede ser negativo")
        if (solicitud.maxUsos < 1) throw ErrorValidacion("max_usos_invalido", "max_uses debe ser al menos 1")
        if ((solicitud.etiqueta?.length ?: 0) > LARGO_MAXIMO_ETIQUETA) {
            throw ErrorValidacion("etiqueta_invalida", "label no puede superar $LARGO_MAXIMO_ETIQUETA caracteres")
        }
        val expiracion = solicitud.expiracion?.let { texto ->
            try {
                Instant.parse(texto)
            } catch (_: DateTimeParseException) {
                throw ErrorValidacion("expiracion_invalida", "expires_at debe venir en ISO-8601 (ej: 2026-01-31T23:59:59Z)")
            }
        } ?: reloj.instant().plus(Duration.ofDays(solicitud.dias.toLong()))
        return repositorio.crearCodigo(
            NuevoCodigo(
                codigo = "$tipo-${parteAleatoria()}",
                etiqueta = solicitud.etiqueta,
                duracionDias = solicitud.dias,
                maxUsos = solicitud.maxUsos,
                expiracion = expiracion,
                tipoLicencia = tipo
            )
        )
    }

    private fun esPremium(suscripcion: Suscripcion) =
        suscripcion.estado == ESTADO_SUSCRIPCION_ACTIVA &&
            !suscripcion.plan.equals(PLAN_GRATUITO, ignoreCase = true) &&
            (suscripcion.expiracion == null || suscripcion.expiracion.isAfter(reloj.instant()))

    /** Sin caracteres ambiguos (0/O, 1/I) y con SecureRandom: los códigos no se pueden adivinar. */
    private fun parteAleatoria(): String =
        (1..LARGO_PARTE_ALEATORIA).map { CARACTERES_CODIGO[generadorAleatorio.nextInt(CARACTERES_CODIGO.length)] }.joinToString("")

    private fun hashear(token: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
