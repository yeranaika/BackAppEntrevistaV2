package PRUEBAS.DOBLES

import CONFIGURACION.ConfiguracionJwt
import INTEGRACIONES.IdentidadGoogle
import INTEGRACIONES.VerificadorIdentidadGoogle
import MODELOS.RefreshToken
import MODELOS.RepositorioCuentaOAuth
import MODELOS.RepositorioRefreshToken
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

val JWT_PRUEBA = ConfiguracionJwt(emisor = "emisor-prueba", audiencia = "audiencia-prueba", secreto = "secreto-prueba")

/** Reloj que la prueba puede adelantar para simular vencimientos. */
class RelojAjustable(private var ahora: Instant = Instant.parse("2026-01-01T00:00:00Z")) : Clock() {
    fun adelantar(duracion: Duration) { ahora = ahora.plus(duracion) }
    override fun instant(): Instant = ahora
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
}

class RefreshTokensEnMemoria : RepositorioRefreshToken {
    val tokens = mutableMapOf<String, RefreshToken>()

    override suspend fun guardar(usuarioId: UUID, hashToken: String, emitidoEn: Instant, expiraEn: Instant) {
        tokens[hashToken] = RefreshToken(UUID.randomUUID(), usuarioId, estaRevocado = false, expiraEn = expiraEn)
    }

    override suspend fun buscarPorHash(hashToken: String) = tokens[hashToken]

    override suspend fun revocarSiActivo(id: UUID, ahora: Instant): Boolean = synchronized(this) {
        val (hash, token) = tokens.entries.firstOrNull { it.value.id == id }?.toPair() ?: return false
        if (token.estaRevocado || !token.expiraEn.isAfter(ahora)) return false
        tokens[hash] = token.copy(estaRevocado = true)
        true
    }

    override suspend fun revocarTodosDelUsuario(usuarioId: UUID): Int {
        val delUsuario = tokens.filterValues { it.usuarioId == usuarioId }
        delUsuario.forEach { (hash, token) -> tokens[hash] = token.copy(estaRevocado = true) }
        return delUsuario.size
    }

    fun sesionesActivas(usuarioId: UUID) = tokens.values.count { it.usuarioId == usuarioId && !it.estaRevocado }
}

/** Vincula por subject; si no existe, usa el usuario con ese correo o crea uno nuevo en `usuarios`. */
class CuentasOAuthEnMemoria(private val usuarios: UsuariosEnMemoria) : RepositorioCuentaOAuth {
    val vinculos = mutableMapOf<String, UUID>()

    override suspend fun vincularOCrearDesdeGoogle(subject: String, correo: String): UUID =
        vinculos.getOrPut(subject) {
            usuarios.buscarPorCorreo(correo)?.id ?: usuarios.agregar(correo, UUID.randomUUID().toString()).id
        }

    override suspend fun esUsuarioGoogle(usuarioId: UUID) = usuarioId in vinculos.values
}

class GoogleEnMemoria(private val identidades: Map<String, IdentidadGoogle> = emptyMap()) : VerificadorIdentidadGoogle {
    override suspend fun verificar(idToken: String) = identidades[idToken]
}
