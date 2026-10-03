package routes.auth

import CONFIGURACION.DIAS_VIGENCIA_REFRESH_TOKEN
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
    val exp = now.plus(DIAS_VIGENCIA_REFRESH_TOKEN, ChronoUnit.DAYS)
    refreshRepo.insert(
        userId = userId,
        tokenHash = hashRefreshToken(plain),
        issuedAt = now,
        expiresAt = exp
    )
}
