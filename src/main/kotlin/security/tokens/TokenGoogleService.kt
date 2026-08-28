package security.tokens

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import routes.auth.TokenPair
import java.util.*
import java.util.concurrent.TimeUnit

object TokenGoogleService {
    fun issue(
        userId: UUID,
        issuer: String,
        audience: String,
        algorithm: Algorithm
    ): TokenPair {
        val now = System.currentTimeMillis()

        val access = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId.toString())
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + TimeUnit.MINUTES.toMillis(15)))
            .sign(algorithm)

        // TODO: genera y persiste el refresh (hash) en tu tabla refresh_token
        val refresh = UUID.randomUUID().toString()

        return TokenPair(access_token = access, refresh_token = refresh)
    }
}
