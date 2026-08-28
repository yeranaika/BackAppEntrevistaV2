package data.repository.admin

import data.models.admin.AdminCreateUserReq
import data.models.admin.AdminCreateUserRes
import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import security.hashPassword
import java.time.LocalDateTime
import java.util.UUID

class AdminUserRepository(
    private val db: Database
) {
    private fun hash(password: String): String = hashPassword(password)

    suspend fun createByAdmin(req: AdminCreateUserReq): AdminCreateUserRes =
        newSuspendedTransaction(db = db) {
            val rol = req.rol.lowercase()
            val correo = req.correo.trim().lowercase() // Normalizar correo
            require(rol == "user" || rol == "admin") { "rol inválido (user|admin)" }
            require(correo.isNotBlank()) { "correo requerido" }
            require(req.contrasena.length >= 6) { "contraseña mínimo 6 caracteres" }

            // DSL nueva: selectAll().where { ... }.limit(1)
            val yaExiste = UsuarioTable
                .selectAll()
                .where { UsuarioTable.correo eq correo }
                .limit(1)
                .any()
            if (yaExiste) error("correo ya registrado")

            val newId = UUID.randomUUID()

            UsuarioTable.insert { st ->
                st[UsuarioTable.usuarioId]      = newId
                st[UsuarioTable.correo]         = correo // Usar correo normalizado
                st[UsuarioTable.contrasenaHash] = hash(req.contrasena)
                st[UsuarioTable.nombre]         = req.nombre
                st[UsuarioTable.idioma]         = req.idioma ?: "es"
                st[UsuarioTable.estado]         = "activo"
                st[UsuarioTable.rol]            = rol
            }

            AdminCreateUserRes(
                id     = newId.toString(),
                correo = correo, // Devolver correo normalizado
                nombre = req.nombre,
                idioma = req.idioma ?: "es",
                rol    = rol
            )
        }

    /** Listar todos los usuarios */
    suspend fun getAllUsers(): List<AdminUserRow> =
        newSuspendedTransaction(db = db) {
            UsuarioTable
                .selectAll()
                .orderBy(UsuarioTable.fechaCreacion to SortOrder.DESC)
                .map { it.toAdminUserRow() }
        }

    /** Obtener un usuario por ID */
    suspend fun getUserById(userId: UUID): AdminUserRow? =
        newSuspendedTransaction(db = db) {
            UsuarioTable
                .selectAll()
                .where { UsuarioTable.usuarioId eq userId }
                .limit(1)
                .firstOrNull()
                ?.toAdminUserRow()
        }

    /** Actualizar rol de un usuario */
    suspend fun updateUserRole(userId: UUID, nuevoRol: String): Int =
        newSuspendedTransaction(db = db) {
            require(nuevoRol == "user" || nuevoRol == "admin") { "Rol inválido: debe ser 'user' o 'admin'" }
            UsuarioTable.update({ UsuarioTable.usuarioId eq userId }) {
                it[rol] = nuevoRol
            }
        }

    /** Desactivar un usuario (cambiar estado a inactivo) */
    suspend fun deleteUser(userId: UUID): Int =
        newSuspendedTransaction(db = db) {
            UsuarioTable.update({ UsuarioTable.usuarioId eq userId }) {
                it[estado] = "inactivo"
            }
        }

    /** Reactivar un usuario (cambiar estado a activo) */
    suspend fun activateUser(userId: UUID): Int =
        newSuspendedTransaction(db = db) {
            UsuarioTable.update({ UsuarioTable.usuarioId eq userId }) {
                it[estado] = "activo"
            }
        }

    /** Verificar si un usuario existe por ID */
    suspend fun existsById(userId: UUID): Boolean =
        newSuspendedTransaction(db = db) {
            UsuarioTable
                .selectAll()
                .where { UsuarioTable.usuarioId eq userId }
                .limit(1)
                .any()
        }

    /** Resetear contraseña de un usuario */
    suspend fun resetPassword(userId: UUID, nuevaContrasena: String): Int =
        newSuspendedTransaction(db = db) {
            require(nuevaContrasena.length >= 6) { "contraseña mínimo 6 caracteres" }
            UsuarioTable.update({ UsuarioTable.usuarioId eq userId }) {
                it[contrasenaHash] = hash(nuevaContrasena)
            }
        }

    suspend fun obtenerDashboardCounts(): DashboardUserCounts =
        newSuspendedTransaction(db = db) {
            val total = UsuarioTable.selectAll().count()
            val admins = UsuarioTable
                .selectAll()
                .where { UsuarioTable.rol eq "admin" }
                .count()
            val regulares = UsuarioTable
                .selectAll()
                .where { UsuarioTable.rol eq "user" }
                .count()

            DashboardUserCounts(
                total = total,
                admins = admins,
                regulares = regulares
            )
        }

    // Mapper para AdminUserRow
    private fun ResultRow.toAdminUserRow() = AdminUserRow(
        usuarioId = this[UsuarioTable.usuarioId],
        correo = this[UsuarioTable.correo],
        nombre = this[UsuarioTable.nombre],
        rol = this[UsuarioTable.rol],
        estado = this[UsuarioTable.estado],
        idioma = this[UsuarioTable.idioma],
        fechaCreacion = this[UsuarioTable.fechaCreacion]
    )
}

// DTO para la respuesta de listado de usuarios
data class AdminUserRow(
    val usuarioId: UUID,
    val correo: String,
    val nombre: String?,
    val rol: String,
    val estado: String,
    val idioma: String,
    val fechaCreacion: LocalDateTime
)

data class DashboardUserCounts(
    val total: Long,
    val admins: Long,
    val regulares: Long
)
