package security

import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64

private val rng = SecureRandom()

/** Genera un refresh token opaco (no JWT). */
fun generateRefreshToken(lengthBytes: Int = 32): String {
    val buf = ByteArray(lengthBytes)
    rng.nextBytes(buf)
    // URL-safe, sin padding: ideal para mandarlo en JSON/headers
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
}

/** Hash para guardar en BD (SHA-256 base64url). */
fun hashRefreshToken(token: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(token.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}
