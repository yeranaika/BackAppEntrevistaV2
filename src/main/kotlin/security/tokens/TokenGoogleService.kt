package security.tokens

import com.auth0.jwt.algorithms.Algorithm
import data.repository.usuarios.RefreshTokenRepository
import routes.auth.TokenPair
import routes.auth.issueNewRefresh
import security.generateRefreshToken
import security.issueAccessToken
import java.util.UUID

object TokenGoogleService {
    suspend fun issue(
        userId: UUID,
        issuer: String,
        audience: String,
        algorithm: Algorithm,
        refreshRepo: RefreshTokenRepository
    ): TokenPair {
        val access = issueAccessToken(
            subject = userId.toString(),
            issuer = issuer,
            audience = audience,
            algorithm = algorithm,
            ttlSeconds = 15 * 60,
            extraClaims = emptyMap()
        )

        val refreshPlain = generateRefreshToken()
        issueNewRefresh(refreshRepo, refreshPlain, userId)

        return TokenPair(access_token = access, refresh_token = refreshPlain)
    }
}
