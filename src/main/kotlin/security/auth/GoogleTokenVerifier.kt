// package tu.paquete.security
package security.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory

class GoogleTokenVerifier(private val serverClientId: String) {
    private val verifier = GoogleIdTokenVerifier.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance()
    )
        .setAudience(listOf(serverClientId))
        .setIssuer("https://accounts.google.com")
        .build()

    fun verify(idTokenStr: String): GoogleIdToken.Payload? =
        verifier.verify(idTokenStr)?.payload
}
