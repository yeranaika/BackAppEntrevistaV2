package PRUEBAS.DOBLES

import ERRORES.ErrorConflicto
import INTEGRACIONES.EnviadorCorreo
import MODELOS.CambiosCuenta
import MODELOS.CambiosPerfil
import MODELOS.CodigoRecuperacion
import MODELOS.ESTADO_ACTIVO
import MODELOS.ESTADO_INACTIVO
import MODELOS.IDIOMA_POR_DEFECTO
import MODELOS.LectorUsuarioSesion
import MODELOS.NuevoUsuario
import MODELOS.ORIGEN_LOCAL
import MODELOS.ObjetivoCarrera
import MODELOS.Perfil
import MODELOS.ROL_USUARIO
import MODELOS.RepositorioObjetivoCarrera
import MODELOS.RepositorioPerfil
import MODELOS.RepositorioRecuperacionContrasena
import MODELOS.RepositorioUsuario
import MODELOS.Usuario
import MODELOS.UsuarioSesion
import UTILIDADES.generarHashContrasena
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

/** Usuarios en memoria: sirve como RepositorioUsuario y como LectorUsuarioSesion, igual que la implementación real. */
class UsuariosEnMemoria(private val perfiles: PerfilesEnMemoria? = null) : RepositorioUsuario, LectorUsuarioSesion {
    private data class Registro(val usuario: Usuario, val hash: String)

    private val registros = linkedMapOf<UUID, Registro>()
    val ultimosLogin = mutableListOf<UUID>()

    val cantidad: Int get() = registros.size

    fun agregar(correo: String, contrasena: String, rol: String = ROL_USUARIO, estaActivo: Boolean = true): UsuarioSesion {
        val id = UUID.randomUUID()
        val usuario = usuarioNuevo(id, correo, rol).copy(estado = if (estaActivo) ESTADO_ACTIVO else ESTADO_INACTIVO)
        registros[id] = Registro(usuario, generarHashContrasena(contrasena))
        return aSesion(registros.getValue(id))
    }

    /** Cambia rol o estado como lo haría un administrador en la BD. */
    fun actualizar(id: UUID, cambio: (UsuarioSesion) -> UsuarioSesion) {
        val registro = registros.getValue(id)
        val nuevo = cambio(aSesion(registro))
        registros[id] = Registro(
            registro.usuario.copy(rol = nuevo.rol, estado = if (nuevo.estaActivo) ESTADO_ACTIVO else ESTADO_INACTIVO),
            nuevo.hashContrasena
        )
    }

    fun hashDe(id: UUID) = registros.getValue(id).hash

    // ---------- RepositorioUsuario ----------

    override suspend fun existeCorreo(correo: String) = registros.values.any { it.usuario.correo == correo }

    override suspend fun crear(nuevo: NuevoUsuario, perfil: CambiosPerfil?): UUID {
        if (existeCorreo(nuevo.correo)) throw ErrorConflicto("email_in_use")
        val id = UUID.randomUUID()
        val usuario = usuarioNuevo(id, nuevo.correo, nuevo.rol).copy(
            nombre = nuevo.nombre,
            idioma = nuevo.idioma,
            telefono = nuevo.telefono,
            fechaNacimiento = nuevo.fechaNacimiento,
            genero = nuevo.genero
        )
        registros[id] = Registro(usuario, nuevo.hashContrasena)
        if (perfil != null && !perfil.estaVacio) perfiles?.guardar(id, perfil)
        return id
    }

    override suspend fun buscarPorId(id: UUID) = registros[id]?.usuario
    override suspend fun listar() = registros.values.map { it.usuario }

    override suspend fun actualizarCuenta(id: UUID, cambios: CambiosCuenta): Boolean =
        modificar(id) { r ->
            r.copy(
                usuario = r.usuario.copy(
                    nombre = cambios.nombre ?: r.usuario.nombre,
                    idioma = cambios.idioma ?: r.usuario.idioma,
                    telefono = if (cambios.telefono != null) cambios.telefono.valor else r.usuario.telefono,
                    fechaNacimiento = if (cambios.fechaNacimiento != null) cambios.fechaNacimiento.valor else r.usuario.fechaNacimiento,
                    genero = if (cambios.genero != null) cambios.genero.valor else r.usuario.genero
                )
            )
        }

    override suspend fun actualizarHashContrasena(id: UUID, hash: String) = modificar(id) { it.copy(hash = hash) }
    override suspend fun actualizarRol(id: UUID, rol: String) = modificar(id) { it.copy(usuario = it.usuario.copy(rol = rol)) }
    override suspend fun actualizarEstado(id: UUID, estado: String) =
        modificar(id) { it.copy(usuario = it.usuario.copy(estado = estado)) }

    override suspend fun eliminar(id: UUID) = registros.remove(id) != null

    // ---------- LectorUsuarioSesion ----------

    override suspend fun buscarPorCorreo(correo: String) =
        registros.values.firstOrNull { it.usuario.correo == correo }?.let(::aSesion)

    override suspend fun buscarSesionPorId(id: UUID) = registros[id]?.let(::aSesion)

    override suspend fun registrarUltimoLogin(id: UUID) { ultimosLogin += id }

    // ---------- Apoyo ----------

    private fun modificar(id: UUID, cambio: (Registro) -> Registro): Boolean {
        val registro = registros[id] ?: return false
        registros[id] = cambio(registro)
        return true
    }

    private fun aSesion(r: Registro) =
        UsuarioSesion(r.usuario.id, r.usuario.correo, r.hash, r.usuario.rol, r.usuario.estado == ESTADO_ACTIVO)

    private fun usuarioNuevo(id: UUID, correo: String, rol: String) = Usuario(
        id = id, correo = correo, nombre = null, idioma = IDIOMA_POR_DEFECTO, rol = rol, estado = ESTADO_ACTIVO,
        telefono = null, origenRegistro = ORIGEN_LOCAL, fechaCreacion = LocalDateTime.now(),
        fechaUltimoLogin = null, fechaNacimiento = null, genero = null
    )
}

class PerfilesEnMemoria : RepositorioPerfil {
    val perfiles = mutableMapOf<UUID, Perfil>()

    override suspend fun buscarPorUsuario(usuarioId: UUID) = perfiles[usuarioId]

    override suspend fun guardar(usuarioId: UUID, cambios: CambiosPerfil) {
        val actual = perfiles[usuarioId] ?: Perfil(usuarioId, null, null, null, null, null)
        perfiles[usuarioId] = actual.copy(
            nivelExperiencia = cambios.nivelExperiencia ?: actual.nivelExperiencia,
            area = cambios.area ?: actual.area,
            pais = cambios.pais ?: actual.pais,
            notaObjetivos = cambios.notaObjetivos ?: actual.notaObjetivos,
            flagsAccesibilidad = cambios.flagsAccesibilidad ?: actual.flagsAccesibilidad
        )
    }
}

class ObjetivosEnMemoria : RepositorioObjetivoCarrera {
    /** Todos los objetivos creados, en orden; el activo es el último no desactivado. */
    val historial = mutableListOf<Pair<UUID, ObjetivoCarrera>>()
    private val inactivos = mutableSetOf<UUID>()

    override suspend fun buscarActivo(usuarioId: UUID) =
        historial.lastOrNull { it.first == usuarioId && it.second.id !in inactivos }?.second

    override suspend fun reemplazarActivo(usuarioId: UUID, nombreCargo: String, sector: String?): ObjetivoCarrera {
        desactivar(usuarioId)
        val objetivo = ObjetivoCarrera(UUID.randomUUID(), nombreCargo, sector)
        historial += usuarioId to objetivo
        return objetivo
    }

    override suspend fun desactivar(usuarioId: UUID): Boolean {
        val activo = buscarActivo(usuarioId) ?: return false
        inactivos += activo.id
        return true
    }
}

class CodigosEnMemoria : RepositorioRecuperacionContrasena {
    data class Registro(val codigo: CodigoRecuperacion, val usado: Boolean)

    val registros = mutableListOf<Registro>()

    override suspend fun crear(usuarioId: UUID, codigo: String, emitidoEn: Instant, expiraEn: Instant) {
        registros.replaceAll { if (it.codigo.usuarioId == usuarioId) it.copy(usado = true) else it }
        registros += Registro(CodigoRecuperacion(UUID.randomUUID(), usuarioId, codigo, expiraEn, 0), usado = false)
    }

    override suspend fun buscarVigente(usuarioId: UUID, ahora: Instant) =
        registros.lastOrNull { it.codigo.usuarioId == usuarioId && !it.usado && it.codigo.expiraEn.isAfter(ahora) }?.codigo

    override suspend fun registrarIntentoFallido(token: UUID) =
        cambiar(token) { it.copy(codigo = it.codigo.copy(intentosFallidos = it.codigo.intentosFallidos + 1)) }

    override suspend fun consumir(token: UUID): Boolean {
        val registro = registros.first { it.codigo.token == token }
        if (registro.usado) return false
        cambiar(token) { it.copy(usado = true) }
        return true
    }

    override suspend fun invalidar(token: UUID) = cambiar(token) { it.copy(usado = true) }

    fun ultimoCodigoDe(usuarioId: UUID) = registros.last { it.codigo.usuarioId == usuarioId }.codigo.codigo

    private fun cambiar(token: UUID, cambio: (Registro) -> Registro) {
        registros.replaceAll { if (it.codigo.token == token) cambio(it) else it }
    }
}

class CorreoEnMemoria : EnviadorCorreo {
    data class Enviado(val destinatario: String, val tipo: String, val codigo: String? = null)

    val enviados = mutableListOf<Enviado>()

    override suspend fun enviarCodigoRecuperacion(correo: String, codigo: String, nombre: String?) {
        enviados += Enviado(correo, "codigo", codigo)
    }

    override suspend fun enviarAvisoCuentaGoogle(correo: String, nombre: String?) {
        enviados += Enviado(correo, "aviso_google")
    }
}
