package MODELOS

import ERRORES.ErrorConflicto
import UTILIDADES.transaccion
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

private const val SQLSTATE_VALOR_DUPLICADO = "23505"

interface RepositorioUsuario {
    suspend fun existeCorreo(correo: String): Boolean

    /** Crea el usuario y, si viene, su perfil en la misma transacción: o se guardan ambos o ninguno. */
    suspend fun crear(nuevo: NuevoUsuario, perfil: CambiosPerfil? = null): UUID

    suspend fun buscarPorId(id: UUID): Usuario?
    suspend fun listar(): List<Usuario>

    /** Devuelve false si el usuario no existe. */
    suspend fun actualizarCuenta(id: UUID, cambios: CambiosCuenta): Boolean
    suspend fun actualizarHashContrasena(id: UUID, hash: String): Boolean
    suspend fun actualizarRol(id: UUID, rol: String): Boolean
    suspend fun actualizarEstado(id: UUID, estado: String): Boolean
    suspend fun eliminar(id: UUID): Boolean
}

/** Una sola implementación sirve a la gestión de usuarios y al inicio de sesión. */
class RepositorioUsuarioExposed : RepositorioUsuario, LectorUsuarioSesion {

    override suspend fun existeCorreo(correo: String): Boolean = transaccion {
        TablaUsuario.selectAll().where { TablaUsuario.correo eq correo }.limit(1).any()
    }

    override suspend fun crear(nuevo: NuevoUsuario, perfil: CambiosPerfil?): UUID = transaccion {
        val id = UUID.randomUUID()
        try {
            TablaUsuario.insert {
                it[usuarioId] = id
                it[correo] = nuevo.correo
                it[contrasenaHash] = nuevo.hashContrasena
                it[nombre] = nuevo.nombre
                it[idioma] = nuevo.idioma
                it[rol] = nuevo.rol
                it[telefono] = nuevo.telefono
                it[origenRegistro] = ORIGEN_LOCAL
                it[fechaNacimiento] = nuevo.fechaNacimiento
                it[genero] = nuevo.genero
            }
        } catch (e: ExposedSQLException) {
            // Dos registros simultáneos con el mismo correo: el índice único decide.
            if (e.sqlState == SQLSTATE_VALOR_DUPLICADO) throw ErrorConflicto("email_in_use", "Ese correo ya está registrado")
            throw e
        }
        if (perfil != null && !perfil.estaVacio) insertarPerfil(id, perfil)
        id
    }

    override suspend fun buscarPorId(id: UUID): Usuario? = transaccion {
        TablaUsuario.selectAll().where { TablaUsuario.usuarioId eq id }.limit(1).firstOrNull()?.aUsuario()
    }

    override suspend fun listar(): List<Usuario> = transaccion {
        TablaUsuario.selectAll().orderBy(TablaUsuario.fechaCreacion to SortOrder.DESC).map { it.aUsuario() }
    }

    override suspend fun actualizarCuenta(id: UUID, cambios: CambiosCuenta): Boolean =
        actualizar(id) { fila ->
            cambios.nombre?.let { fila[TablaUsuario.nombre] = it }
            cambios.idioma?.let { fila[TablaUsuario.idioma] = it }
            cambios.telefono?.let { fila[TablaUsuario.telefono] = it.valor }
            cambios.fechaNacimiento?.let { fila[TablaUsuario.fechaNacimiento] = it.valor }
            cambios.genero?.let { fila[TablaUsuario.genero] = it.valor }
        }

    override suspend fun actualizarHashContrasena(id: UUID, hash: String) =
        actualizar(id) { it[TablaUsuario.contrasenaHash] = hash }

    override suspend fun actualizarRol(id: UUID, rol: String) = actualizar(id) { it[TablaUsuario.rol] = rol }

    override suspend fun actualizarEstado(id: UUID, estado: String) = actualizar(id) { it[TablaUsuario.estado] = estado }

    // ON DELETE CASCADE en la BD borra sesiones, perfil, objetivos, consentimientos y suscripciones.
    override suspend fun eliminar(id: UUID): Boolean = transaccion {
        TablaUsuario.deleteWhere { usuarioId eq id } > 0
    }

    // ---------- LectorUsuarioSesion ----------

    override suspend fun buscarPorCorreo(correo: String): UsuarioSesion? = transaccion {
        TablaUsuario.selectAll().where { TablaUsuario.correo eq correo }.limit(1).firstOrNull()?.aUsuarioSesion()
    }

    override suspend fun buscarSesionPorId(id: UUID): UsuarioSesion? = transaccion {
        TablaUsuario.selectAll().where { TablaUsuario.usuarioId eq id }.limit(1).firstOrNull()?.aUsuarioSesion()
    }

    override suspend fun registrarUltimoLogin(id: UUID) {
        actualizar(id) { it[TablaUsuario.fechaUltimoLogin] = LocalDateTime.now() }
    }

    // ---------- Apoyo ----------

    private suspend fun actualizar(id: UUID, cambios: TablaUsuario.(UpdateBuilder<Int>) -> Unit): Boolean = transaccion {
        TablaUsuario.update({ TablaUsuario.usuarioId eq id }) { cambios(it) } > 0
    }

    private fun insertarPerfil(usuarioId: UUID, perfil: CambiosPerfil) {
        TablaPerfil.insert {
            it[perfilId] = UUID.randomUUID()
            it[TablaPerfil.usuarioId] = usuarioId
            it[nivelExperiencia] = perfil.nivelExperiencia?.valorBd
            it[area] = perfil.area
            it[pais] = perfil.pais
            it[notaObjetivos] = perfil.notaObjetivos
            it[flagsAccesibilidad] = perfil.flagsAccesibilidad
            it[fechaActualizacion] = java.time.OffsetDateTime.now()
        }
    }

    private fun ResultRow.aUsuario() = Usuario(
        id = this[TablaUsuario.usuarioId],
        correo = this[TablaUsuario.correo],
        nombre = this[TablaUsuario.nombre],
        idioma = this[TablaUsuario.idioma],
        rol = this[TablaUsuario.rol],
        estado = this[TablaUsuario.estado],
        telefono = this[TablaUsuario.telefono],
        origenRegistro = this[TablaUsuario.origenRegistro],
        fechaCreacion = this[TablaUsuario.fechaCreacion],
        fechaUltimoLogin = this[TablaUsuario.fechaUltimoLogin],
        fechaNacimiento = this[TablaUsuario.fechaNacimiento],
        genero = this[TablaUsuario.genero]
    )

    private fun ResultRow.aUsuarioSesion() = UsuarioSesion(
        id = this[TablaUsuario.usuarioId],
        correo = this[TablaUsuario.correo],
        hashContrasena = this[TablaUsuario.contrasenaHash],
        rol = this[TablaUsuario.rol],
        estaActivo = this[TablaUsuario.estado] == ESTADO_ACTIVO
    )
}
