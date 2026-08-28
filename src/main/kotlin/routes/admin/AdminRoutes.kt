package routes.admin

import data.models.admin.AdminCreateUserReq
import data.repository.admin.AdminUserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.adminRoutes(adminUserRepo: AdminUserRepository) {
    route("/admin") {
        authenticate("auth-jwt") {
            // Middleware: verificar que el usuario es admin
            intercept(ApplicationCallPipeline.Call) {
                val principal = call.principal<JWTPrincipal>()
                val role = principal?.payload?.getClaim("role")?.asString()

                if (role != "admin") {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Acceso denegado: se requiere rol de administrador"))
                    finish()
                }
            }

            // GET /admin/usuarios - Listar todos los usuarios
            get("/usuarios") {
                try {
                    val users = adminUserRepo.getAllUsers()
                    val response = users.map { user ->
                        UserListResponse(
                            usuarioId = user.usuarioId.toString(),
                            correo = user.correo,
                            nombre = user.nombre,
                            rol = user.rol,
                            estado = user.estado,
                            idioma = user.idioma,
                            fechaCreacion = user.fechaCreacion.toString()
                        )
                    }
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.application.environment.log.error("Error al listar usuarios", e)
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error al obtener lista de usuarios"))
                }
            }

            // POST /admin/usuarios - Crear un nuevo usuario
            post("/usuarios") {
                try {
                    val req = call.receive<AdminCreateUserReq>()
                    val newUser = adminUserRepo.createByAdmin(req)
                    call.respond(HttpStatusCode.Created, newUser)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Solicitud inv�lida"))
                } catch (e: IllegalStateException) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Conflicto al crear usuario"))
                } catch (e: Exception) {
                    call.application.environment.log.error("Error al crear usuario", e)
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error al crear usuario"))
                }
            }

            // PATCH /admin/usuarios/{usuarioId}/rol - Cambiar rol de un usuario
            patch("/usuarios/{usuarioId}/rol") {
                try {
                    val usuarioIdStr = call.parameters["usuarioId"]
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("usuarioId requerido"))

                    val usuarioId = try {
                        UUID.fromString(usuarioIdStr)
                    } catch (e: IllegalArgumentException) {
                        return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("usuarioId inv�lido"))
                    }

                    val req = call.receive<UpdateRoleRequest>()

                    // Verificar que el usuario existe
                    if (!adminUserRepo.existsById(usuarioId)) {
                        return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Usuario no encontrado"))
                    }

                    // Actualizar el rol
                    val updated = adminUserRepo.updateUserRole(usuarioId, req.nuevoRol)

                    if (updated > 0) {
                        call.respond(HttpStatusCode.OK, SuccessResponse("Rol actualizado exitosamente"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("No se pudo actualizar el rol"))
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Rol inv�lido"))
                } catch (e: Exception) {
                    call.application.environment.log.error("Error al actualizar rol", e)
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error al actualizar rol"))
                }
            }

            // DELETE /admin/usuarios/{usuarioId} - Desactivar un usuario
            delete("/usuarios/{usuarioId}") {
                try {
                    val usuarioIdStr = call.parameters["usuarioId"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("usuarioId requerido"))

                    val usuarioId = try {
                        UUID.fromString(usuarioIdStr)
                    } catch (e: IllegalArgumentException) {
                        return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("usuarioId inv�lido"))
                    }

                    // Verificar que el usuario existe
                    if (!adminUserRepo.existsById(usuarioId)) {
                        return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Usuario no encontrado"))
                    }

                    // Desactivar el usuario
                    val deleted = adminUserRepo.deleteUser(usuarioId)

                    if (deleted > 0) {
                        call.respond(HttpStatusCode.OK, SuccessResponse("Usuario desactivado exitosamente"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("No se pudo desactivar el usuario"))
                    }
                } catch (e: Exception) {
                    call.application.environment.log.error("Error al desactivar usuario", e)
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error al desactivar usuario"))
                }
            }

            // PATCH /admin/usuarios/{usuarioId}/activar - Activar un usuario
            patch("/usuarios/{usuarioId}/activar") {
                try {
                    val usuarioIdStr = call.parameters["usuarioId"]
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("usuarioId requerido"))

                    val usuarioId = try {
                        UUID.fromString(usuarioIdStr)
                    } catch (e: IllegalArgumentException) {
                        return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("usuarioId inválido"))
                    }

                    // Verificar que el usuario existe
                    if (!adminUserRepo.existsById(usuarioId)) {
                        return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Usuario no encontrado"))
                    }

                    // Activar el usuario
                    val activated = adminUserRepo.activateUser(usuarioId)

                    if (activated > 0) {
                        call.respond(HttpStatusCode.OK, SuccessResponse("Usuario activado exitosamente"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("No se pudo activar el usuario"))
                    }
                } catch (e: Exception) {
                    call.application.environment.log.error("Error al activar usuario", e)
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error al activar usuario"))
                }
            }

            // PATCH /admin/usuarios/{usuarioId}/password - Resetear contraseña de un usuario
            patch("/usuarios/{usuarioId}/password") {
                try {
                    val usuarioIdStr = call.parameters["usuarioId"]
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("usuarioId requerido"))

                    val usuarioId = try {
                        UUID.fromString(usuarioIdStr)
                    } catch (e: IllegalArgumentException) {
                        return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("usuarioId inválido"))
                    }

                    val req = call.receive<ResetPasswordRequest>()

                    // Verificar que el usuario existe
                    if (!adminUserRepo.existsById(usuarioId)) {
                        return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Usuario no encontrado"))
                    }

                    // Resetear la contraseña
                    val updated = adminUserRepo.resetPassword(usuarioId, req.nuevaContrasena)

                    if (updated > 0) {
                        call.respond(HttpStatusCode.OK, SuccessResponse("Contraseña actualizada exitosamente"))
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("No se pudo actualizar la contraseña"))
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Contraseña inválida"))
                } catch (e: Exception) {
                    call.application.environment.log.error("Error al resetear contraseña", e)
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error al resetear contraseña"))
                }
            }
        }
    }
}
