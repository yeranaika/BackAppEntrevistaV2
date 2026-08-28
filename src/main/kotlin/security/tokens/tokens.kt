package security.token

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Date

private val rng = SecureRandom()

fun issueAccessToken(
    userId: String,
    issuer: String,
    audience: String,
    algorithm: Algorithm,
    ttlSeconds: Long = 45 * 60
): String {
    val now = Instant.now()
    return JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withSubject(userId)
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(now.plusSeconds(ttlSeconds)))
        .sign(algorithm)
}
