package INTEGRACIONES

import CONFIGURACION.ConfiguracionCorreo
import CONFIGURACION.MINUTOS_VIGENCIA_CODIGO_RECUPERACION
import ERRORES.ErrorServicioExterno
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Year
import java.util.Properties

private const val NOMBRE_REMITENTE = "EntrevistaAPP"
private const val PUERTO_SMTP_SSL = 465
private const val TIMEOUT_SMTP_MS = "10000"

interface EnviadorCorreo {
    suspend fun enviarCodigoRecuperacion(correo: String, codigo: String, nombre: String?)

    /** Las cuentas creadas con Google no tienen contraseña propia: se les avisa en vez de mandar código. */
    suspend fun enviarAvisoCuentaGoogle(correo: String, nombre: String?)
}

class ClienteCorreoSmtp(private val config: ConfiguracionCorreo) : EnviadorCorreo {

    private val log = LoggerFactory.getLogger(ClienteCorreoSmtp::class.java)

    private val sesion: Session by lazy {
        val propiedades = Properties().apply {
            put("mail.smtp.host", config.hostSmtp)
            put("mail.smtp.port", config.puertoSmtp.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("mail.smtp.ssl.trust", config.hostSmtp)
            if (config.puertoSmtp == PUERTO_SMTP_SSL) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
            put("mail.smtp.connectiontimeout", TIMEOUT_SMTP_MS)
            put("mail.smtp.timeout", TIMEOUT_SMTP_MS)
            put("mail.smtp.writetimeout", TIMEOUT_SMTP_MS)
        }
        Session.getInstance(propiedades, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(config.usuario, config.contrasena)
        })
    }

    override suspend fun enviarCodigoRecuperacion(correo: String, codigo: String, nombre: String?) {
        val cuerpo = """
            <h2>Recuperación de contraseña</h2>
            <p>${saludo(nombre)}</p>
            <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta en <strong>EntrevistaAPP</strong>.</p>
            <p>Tu código de recuperación es:</p>
            <div class="code-box"><div class="code">${escaparHtml(codigo)}</div></div>
            <div class="warning"><strong>Este código expira en $MINUTOS_VIGENCIA_CODIGO_RECUPERACION minutos.</strong></div>
            <p>Si no solicitaste este cambio, ignora este correo: tu contraseña no se modificará.</p>
        """.trimIndent()
        enviar(correo, "Código de recuperación - EntrevistaAPP", cuerpo)
    }

    override suspend fun enviarAvisoCuentaGoogle(correo: String, nombre: String?) {
        val cuerpo = """
            <h2>Recuperación de contraseña</h2>
            <p>${saludo(nombre)}</p>
            <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta, pero tu cuenta
            se creó con <strong>Google</strong> y no tiene una contraseña propia.</p>
            <p>Para entrar, usa el botón <strong>Continuar con Google</strong> en la aplicación.</p>
            <p>Si no hiciste esta solicitud, puedes ignorar este correo.</p>
        """.trimIndent()
        enviar(correo, "Tu cuenta usa Google - EntrevistaAPP", cuerpo)
    }

    private suspend fun enviar(destinatario: String, asunto: String, contenido: String) {
        // Jakarta Mail es bloqueante: no debe ocupar los hilos de Ktor.
        withContext(Dispatchers.IO) {
            try {
                val mensaje = MimeMessage(sesion).apply {
                    setFrom(InternetAddress(config.usuario, NOMBRE_REMITENTE))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario))
                    setSubject(asunto, "UTF-8")
                    setContent(plantilla(contenido), "text/html; charset=UTF-8")
                }
                Transport.send(mensaje)
                log.info("Correo '{}' enviado", asunto)
            } catch (e: MessagingException) {
                throw ErrorServicioExterno("correo_no_disponible", "No se pudo enviar el correo", e)
            }
        }
    }

    // El nombre lo escribe el usuario: sin escapar permitiría inyectar HTML en el correo.
    private fun saludo(nombre: String?) = if (nombre.isNullOrBlank()) "Hola," else "Hola ${escaparHtml(nombre)},"

    private fun escaparHtml(texto: String) = texto
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun plantilla(contenido: String) = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                .container { max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f4f4f4; }
                .content { background-color: white; padding: 30px; border-radius: 8px; }
                .code-box { background-color: #f0f0f0; border: 2px solid #667eea; border-radius: 8px; padding: 20px; text-align: center; margin: 20px 0; }
                .code { font-size: 32px; font-weight: bold; color: #667eea; letter-spacing: 8px; }
                .warning { background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 12px; margin: 20px 0; }
                .footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="content">
                    $contenido
                    <p>Saludos,<br>El equipo de EntrevistaAPP</p>
                </div>
                <div class="footer">
                    <p>Este es un correo automático, por favor no respondas a este mensaje.</p>
                    <p>&copy; ${Year.now().value} EntrevistaAPP. Todos los derechos reservados.</p>
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()
}
