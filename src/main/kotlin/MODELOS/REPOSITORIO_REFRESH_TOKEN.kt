package MODELOS

import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

interface RepositorioRefreshToken {
    suspend fun guardar(usuarioId: UUID, hashToken: String, emitidoEn: Instant, expiraEn: Instant)

    /** Busca por hash aunque esté revocado o vencido: el servicio necesita distinguir la reutilización. */
    suspend fun buscarPorHash(hashToken: String): RefreshToken?

    /**
     * Revoca el token solo si sigue activo. Devuelve true únicamente a la llamada que lo revocó,
     * así dos renovaciones simultáneas con el mismo token no pueden ganar ambas.
     */
    suspend fun revocarSiActivo(id: UUID, ahora: Instant): Boolean

    suspend fun revocarTodosDelUsuario(usuarioId: UUID): Int
}

class RepositorioRefreshTokenExposed : RepositorioRefreshToken {

    override suspend fun guardar(usuarioId: UUID, hashToken: String, emitidoEn: Instant, expiraEn: Instant) {
        transaccion {
            TablaRefreshToken.insert {
                it[refreshId] = UUID.randomUUID()
                it[TablaRefreshToken.usuarioId] = usuarioId
                it[tokenHash] = hashToken
                it[TablaRefreshToken.emitidoEn] = emitidoEn
                it[TablaRefreshToken.expiraEn] = expiraEn
                it[revocado] = false
            }
        }
    }

    override suspend fun buscarPorHash(hashToken: String): RefreshToken? = transaccion {
        TablaRefreshToken
            .selectAll()
            .where { TablaRefreshToken.tokenHash eq hashToken }
            .limit(1)
            .firstOrNull()
            ?.aRefreshToken()
    }

    override suspend fun revocarSiActivo(id: UUID, ahora: Instant): Boolean = transaccion {
        val filasRevocadas = TablaRefreshToken.update({
            (TablaRefreshToken.refreshId eq id) and
                (TablaRefreshToken.revocado eq false) and
                (TablaRefreshToken.expiraEn greater ahora)
        }) {
            it[revocado] = true
        }
        filasRevocadas == 1
    }

    override suspend fun revocarTodosDelUsuario(usuarioId: UUID): Int = transaccion {
        TablaRefreshToken.update({ TablaRefreshToken.usuarioId eq usuarioId }) {
            it[revocado] = true
        }
    }

    private fun ResultRow.aRefreshToken() = RefreshToken(
        id = this[TablaRefreshToken.refreshId],
        usuarioId = this[TablaRefreshToken.usuarioId],
        estaRevocado = this[TablaRefreshToken.revocado],
        expiraEn = this[TablaRefreshToken.expiraEn]
    )
}
