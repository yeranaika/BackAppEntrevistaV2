package services

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

class EmailService(
    private val smtpHost: String,
    private val smtpPort: Int,
    private val username: String,
    private val password: String,
    private val fromEmail: String
) {
    private val session: Session by lazy {
        val props = Properties().apply {
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPort.toString())
            put("mail.smtp.auth", "true")

            // Configuración según el puerto
            if (smtpPort == 465) {
                // Puerto 465: SSL/TLS directo
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
                put("mail.smtp.ssl.trust", smtpHost)
            } else {
                // Puerto 587: STARTTLS
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
                put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
                put("mail.smtp.ssl.trust", smtpHost)
            }

            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
        }

        Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password)
            }
        })
    }

    /**
     * Envía un correo de recuperación de contraseña
     */
    fun sendRecoveryCode(toEmail: String, codigo: String, nombreUsuario: String? = null) {
        val subject = "Código de recuperación - EntrevistaAPP"
        val body = buildRecoveryEmailBody(codigo, nombreUsuario)

        sendEmail(toEmail, subject, body)
    }

    /**
     * Construye el cuerpo del email de recuperación
     */
    private fun buildRecoveryEmailBody(codigo: String, nombreUsuario: String?): String {
        val saludo = if (nombreUsuario != null) "Hola $nombreUsuario," else "Hola,"

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                    }
                    .container {
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                        background-color: #f4f4f4;
                    }
                    .content {
                        background-color: white;
                        padding: 30px;
                        border-radius: 8px;
                    }
                    .code-box {
                        background-color: #f0f0f0;
                        border: 2px solid #667eea;
                        border-radius: 8px;
                        padding: 20px;
                        text-align: center;
                        margin: 20px 0;
                    }
                    .code {
                        font-size: 32px;
                        font-weight: bold;
                        color: #667eea;
                        letter-spacing: 8px;
                    }
                    .warning {
                        background-color: #fff3cd;
                        border-left: 4px solid #ffc107;
                        padding: 12px;
                        margin: 20px 0;
                    }
                    .footer {
                        text-align: center;
                        margin-top: 20px;
                        color: #666;
                        font-size: 12px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="content">
                        <h2>Recuperación de Contraseña</h2>
                        <p>$saludo</p>
                        <p>Hemos recibido una solicitud para restablecer la contraseña de tu cuenta en <strong>EntrevistaAPP</strong>.</p>

                        <p>Tu código de recuperación es:</p>

                        <div class="code-box">
                            <div class="code">$codigo</div>
                        </div>

                        <div class="warning">
                            <strong>⏱️ Este código expirará en 15 minutos.</strong>
                        </div>

                        <p>Si no solicitaste este cambio, puedes ignorar este correo de forma segura. Tu contraseña no será modificada.</p>

                        <p>Para restablecer tu contraseña, ingresa este código en la aplicación.</p>

                        <p>Saludos,<br>El equipo de EntrevistaAPP</p>
                    </div>

                    <div class="footer">
                        <p>Este es un correo automático, por favor no respondas a este mensaje.</p>
                        <p>&copy; ${java.time.Year.now().value} EntrevistaAPP. Todos los derechos reservados.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Método genérico para enviar emails
     */
    private fun sendEmail(to: String, subject: String, body: String) {
        try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(fromEmail, "EntrevistaAPP"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject, "UTF-8")
                setContent(body, "text/html; charset=UTF-8")
            }

            Transport.send(message)
            println("✅ Email enviado exitosamente a: $to")
        } catch (e: MessagingException) {
            println("❌ Error al enviar email a $to: ${e.message}")
            throw RuntimeException("Error al enviar email: ${e.message}", e)
        }
    }
}
