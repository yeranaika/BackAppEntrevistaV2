package MODELOS

import UTILIDADES.generarHashContrasena
import UTILIDADES.transaccion
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

private const val PROVEEDOR_GOOGLE = "google"

interface RepositorioCuentaOAuth {
    /** Devuelve el usuario vinculado a la cuenta de Google; lo enlaza por correo o lo crea si no existe. */
    suspend fun vincularOCrearDesdeGoogle(subject: String, correo: String): UUID

    suspend fun esUsuarioGoogle(usuarioId: UUID): Boolean
}

class RepositorioCuentaOAuthExposed : RepositorioCuentaOAuth {

    override suspend fun esUsuarioGoogle(usuarioId: UUID): Boolean = transaccion {
        TablaCuentaOAuth
            .selectAll()
            .where { (TablaCuentaOAuth.usuarioId eq usuarioId) and (TablaCuentaOAuth.proveedor eq PROVEEDOR_GOOGLE) }
            .limit(1)
            .firstOrNull() != null
    }

    override suspend fun vincularOCrearDesdeGoogle(subject: String, correo: String): UUID = transaccion {
        val vinculado = TablaCuentaOAuth
            .selectAll()
            .where { (TablaCuentaOAuth.proveedor eq PROVEEDOR_GOOGLE) and (TablaCuentaOAuth.subject eq subject) }
            .limit(1)
            .firstOrNull()
            ?.get(TablaCuentaOAuth.usuarioId)
        if (vinculado != null) return@transaccion vinculado

        val usuarioId = buscarUsuarioPorCorreo(correo) ?: crearUsuarioGoogle(correo)
        TablaCuentaOAuth.insert {
            it[oauthId] = UUID.randomUUID()
            it[proveedor] = PROVEEDOR_GOOGLE
            it[TablaCuentaOAuth.subject] = subject
            it[TablaCuentaOAuth.correo] = correo
            it[correoVerificado] = true
            it[TablaCuentaOAuth.usuarioId] = usuarioId
        }
        usuarioId
    }

    private fun buscarUsuarioPorCorreo(correo: String): UUID? =
        TablaUsuario
            .selectAll()
            .where { TablaUsuario.correo eq correo }
            .limit(1)
            .firstOrNull()
            ?.get(TablaUsuario.usuarioId)

    private fun crearUsuarioGoogle(correo: String): UUID {
        val id = UUID.randomUUID()
        TablaUsuario.insert {
            it[usuarioId] = id
            it[TablaUsuario.correo] = correo
            it[nombre] = correo.substringBefore("@")
            // La columna es NOT NULL; una contraseña aleatoria que nadie conoce impide el login local.
            it[contrasenaHash] = generarHashContrasena(UUID.randomUUID().toString())
            it[idioma] = IDIOMA_POR_DEFECTO
            it[estado] = ESTADO_ACTIVO
            it[rol] = ROL_USUARIO
            it[origenRegistro] = ORIGEN_GOOGLE
        }
        return id
    }
}
