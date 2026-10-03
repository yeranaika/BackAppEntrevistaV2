package INTEGRACIONES

import ERRORES.ErrorServicioExterno
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException

private const val EMISOR_GOOGLE = "https://accounts.google.com"

data class IdentidadGoogle(
    val subject: String,
    val correo: String?,
    val correoVerificado: Boolean
)

interface VerificadorIdentidadGoogle {
    /** Identidad del idToken, o null si el token es inválido, vencido o de otra app. */
    suspend fun verificar(idToken: String): IdentidadGoogle?
}

class ClienteGoogleIdentidad(clientId: String) : VerificadorIdentidadGoogle {

    private val fabricaJson = GsonFactory.getDefaultInstance()
    private val verificador = GoogleIdTokenVerifier.Builder(NetHttpTransport(), fabricaJson)
        .setAudience(listOf(clientId))
        .setIssuer(EMISOR_GOOGLE)
        .build()

    override suspend fun verificar(idToken: String): IdentidadGoogle? {
        val token = parsear(idToken) ?: return null
        val esValido = withContext(Dispatchers.IO) { verificarFirma(token) }
        if (!esValido) return null
        val datos = token.payload
        return IdentidadGoogle(
            subject = datos.subject,
            correo = datos.email,
            correoVerificado = datos.emailVerified == true
        )
    }

    // Parsear no usa red: cualquier fallo aquí es un token malformado.
    private fun parsear(idToken: String): GoogleIdToken? =
        try {
            GoogleIdToken.parse(fabricaJson, idToken)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IOException) {
            null
        }

    // Verificar descarga las llaves públicas de Google: un IOException aquí es caída de red, no token inválido.
    private fun verificarFirma(token: GoogleIdToken): Boolean =
        try {
            verificador.verify(token)
        } catch (_: GeneralSecurityException) {
            false
        } catch (e: IOException) {
            throw ErrorServicioExterno("google_no_disponible", "No se pudo validar el token con Google", e)
        }
}
