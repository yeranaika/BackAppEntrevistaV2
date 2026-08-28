package routes.auth

import data.repository.usuarios.RefreshTokenRepository
import security.hashRefreshToken
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Helper para persistir un refresh nuevo (hash + expiración) */
suspend fun issueNewRefresh(
    refreshRepo: RefreshTokenRepository,
    plain: String,
    userId: UUID
) {
    val now = Instant.now()
    val exp = now.plus(15, ChronoUnit.DAYS)
    refreshRepo.insert(
        userId = userId,
        tokenHash = hashRefreshToken(plain),
        issuedAt = now,
        expiresAt = exp
    )
}
