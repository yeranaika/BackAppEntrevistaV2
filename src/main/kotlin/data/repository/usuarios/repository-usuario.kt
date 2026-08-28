package data.repository.usuarios

import data.tables.usuarios.UsuarioTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class UserRow(
    val id: UUID,
    val email: String,
    val hash: String,
    val nombre: String?,
    val idioma: String?,
    val rol: String,
    // NUEVOS CAMPOS DEL MODELO
    val estado: String = "activo",
    val telefono: String? = null,
    val origenRegistro: String = "local",
    val fechaUltimoLogin: LocalDateTime? = null,
    val fechaNacimiento: LocalDate? = null,
    val genero: String? = null
)

private suspend fun <T> dbTx(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, statement = block)

class EmailAlreadyInUseException(val email: String, cause: Throwable? = null) : RuntimeException(cause)

class UserRepository {

    /** ¿Existe un usuario con ese correo? */
    suspend fun existsByEmail(email: String): Boolean = dbTx {
        UsuarioTable
            .selectAll()
            .where { UsuarioTable.correo eq email }
            .limit(1)
            .any()
    }

    /** Crea un usuario y devuelve su UUID. */
    suspend fun create(
        email: String,
        hash: String,
        nombre: String?,
        idioma: String?,
        rol: String = "user",
        telefono: String? = null,
        origenRegistro: String = "local",           // local / google / otros
        fechaNacimiento: LocalDate? = null,
        genero: String? = null
    ): UUID = dbTx {
        val id = UUID.randomUUID()
        try {
            // Validación suave para que coincida con el CHECK de la BD
            require(origenRegistro in setOf("local", "google", "otros")) {
                "origenRegistro inválido: $origenRegistro"
            }

            UsuarioTable.insert {
                it[usuarioId]      = id
                it[correo]         = email
                it[contrasenaHash] = hash
                it[UsuarioTable.nombre] = nombre

                if (idioma != null) it[UsuarioTable.idioma] = idioma
                it[UsuarioTable.rol]    = rol

                if (telefono != null) it[UsuarioTable.telefono] = telefono
                it[UsuarioTable.origenRegistro] = origenRegistro

                if (fechaNacimiento != null) it[UsuarioTable.fechaNacimiento] = fechaNacimiento
                if (genero != null)          it[UsuarioTable.genero] = genero

                // fecha_creacion se llena con clientDefault o DEFAULT de la BD
            }
            id
        } catch (e: ExposedSQLException) {
            // unique_violation
            if (e.sqlState == "23505") throw EmailAlreadyInUseException(email, e)
            throw e
        }
    }

    /** Busca por email. */
    suspend fun findByEmail(email: String): UserRow? = dbTx {
        UsuarioTable
            .selectAll()
            .where { UsuarioTable.correo eq email }
            .limit(1)
            .firstOrNull()
            ?.toUserRow()
    }

    /** Busca por ID. */
    suspend fun findById(id: UUID): UserRow? = dbTx {
        UsuarioTable
            .selectAll()
            .where { UsuarioTable.usuarioId eq id }
            .limit(1)
            .firstOrNull()
            ?.toUserRow()
    }

    /** Actualiza solo el nombre. */
    suspend fun updateNombre(id: UUID, nuevoNombre: String?): Int = dbTx {
        UsuarioTable.update({ UsuarioTable.usuarioId eq id }) {
            it[nombre] = nuevoNombre
        }
    }

    /** Actualiza solo el idioma. */
    suspend fun updateIdioma(id: UUID, nuevoIdioma: String?): Int = dbTx {
        if (nuevoIdioma == null) return@dbTx 0
        UsuarioTable.update({ UsuarioTable.usuarioId eq id }) {
            it[idioma] = nuevoIdioma
        }
    }

    /** Actualiza el hash de contraseña. */
    suspend fun updatePasswordHash(id: UUID, newHash: String): Int = dbTx {
        UsuarioTable.update({ UsuarioTable.usuarioId eq id }) {
            it[contrasenaHash] = newHash
        }
    }

    /** Alias usado por rutas. */
    suspend fun updatePassword(userId: UUID, newHash: String): Int =
        updatePasswordHash(userId, newHash)

    /** Cambia el rol (user/admin). */
    suspend fun updateRol(userId: UUID, rol: String): Int = dbTx {
        require(rol == "user" || rol == "admin") { "rol inválido" }
        UsuarioTable.update({ UsuarioTable.usuarioId eq userId }) {
            it[UsuarioTable.rol] = rol
        }
    }

    /** ¿Es admin? (consulta directa en BD). */
    suspend fun isAdmin(userId: UUID): Boolean = dbTx {
        UsuarioTable
            .selectAll()
            .where { (UsuarioTable.usuarioId eq userId) and (UsuarioTable.rol eq "admin") }
            .limit(1)
            .any()
    }

    /** Elimina al usuario y devuelve true si existía. */
    suspend fun deleteById(userId: UUID): Boolean = dbTx {
        UsuarioTable.deleteWhere { UsuarioTable.usuarioId eq userId } > 0
    }

    /** Actualiza fecha_ultimo_login al momento actual. */
    suspend fun touchUltimoLogin(userId: UUID, fecha: LocalDateTime = LocalDateTime.now()): Int = dbTx {
        UsuarioTable.update({ UsuarioTable.usuarioId eq userId }) {
            it[fechaUltimoLogin] = fecha
        }
    }

    /** Cambia el teléfono. */
    suspend fun updateTelefono(userId: UUID, telefono: String?): Int = dbTx {
        UsuarioTable.update({ UsuarioTable.usuarioId eq userId }) {
            it[UsuarioTable.telefono] = telefono
        }
    }

    /** Actualiza fecha de nacimiento y género. */
    suspend fun updateDatosDemograficos(
        userId: UUID,
        fechaNacimiento: LocalDate?,
        genero: String?
    ): Int = dbTx {
        UsuarioTable.update({ UsuarioTable.usuarioId eq userId }) {
            it[UsuarioTable.fechaNacimiento] = fechaNacimiento
            it[UsuarioTable.genero] = genero
        }
    }

    // ---------- Mapper ----------
    private fun ResultRow.toUserRow() = UserRow(
        id     = this[UsuarioTable.usuarioId],
        email  = this[UsuarioTable.correo],
        hash   = this[UsuarioTable.contrasenaHash],
        nombre = this[UsuarioTable.nombre],
        idioma = this[UsuarioTable.idioma],
        rol    = this[UsuarioTable.rol],

        estado         = this[UsuarioTable.estado],
        telefono       = this[UsuarioTable.telefono],
        origenRegistro = this[UsuarioTable.origenRegistro],
        fechaUltimoLogin = this[UsuarioTable.fechaUltimoLogin],
        fechaNacimiento  = this[UsuarioTable.fechaNacimiento],
        genero           = this[UsuarioTable.genero]
    )
}
