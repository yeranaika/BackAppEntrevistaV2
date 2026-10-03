package VISTAS

import ESQUEMAS.RespuestaCuenta
import ESQUEMAS.RespuestaObjetivo
import ESQUEMAS.RespuestaOnboarding
import ESQUEMAS.RespuestaPerfil
import ESQUEMAS.RespuestaUsuarioAdmin
import ESQUEMAS.RespuestaUsuarioCreado
import MODELOS.ObjetivoCarrera
import MODELOS.Perfil
import MODELOS.Usuario
import SERVICIOS.CuentaCompleta
import SERVICIOS.ResumenOnboarding

// Presentación: de los modelos de dominio al JSON de la API.
// El nivel se guarda como junior/semisenior/senior pero la app Android usa jr/mid/sr.

fun CuentaCompleta.aRespuesta() = RespuestaCuenta(
    id = usuario.id.toString(),
    correo = usuario.correo,
    nombre = usuario.nombre,
    idioma = usuario.idioma,
    telefono = usuario.telefono,
    genero = usuario.genero,
    fechaNacimiento = usuario.fechaNacimiento?.toString(),
    estado = usuario.estado,
    origenRegistro = usuario.origenRegistro,
    fechaUltimoLogin = usuario.fechaUltimoLogin?.toString(),
    perfil = perfil?.aRespuesta(),
    meta = objetivo?.nombreCargo
)

fun Perfil.aRespuesta() = RespuestaPerfil(
    nivelExperiencia = nivelExperiencia?.codigoApp,
    area = area,
    pais = pais,
    notaObjetivos = notaObjetivos,
    flagsAccesibilidad = flagsAccesibilidad
)

fun ObjetivoCarrera.aRespuesta() = RespuestaObjetivo(
    id = id.toString(),
    nombreCargo = nombreCargo,
    sector = sector
)

fun ResumenOnboarding.aRespuesta() = RespuestaOnboarding(
    area = area,
    nivelExperiencia = nivelExperiencia.codigoApp,
    nombreCargo = nombreCargo,
    descripcionObjetivo = descripcionObjetivo
)

fun Usuario.aRespuestaAdmin() = RespuestaUsuarioAdmin(
    usuarioId = id.toString(),
    correo = correo,
    nombre = nombre,
    rol = rol,
    estado = estado,
    idioma = idioma,
    fechaCreacion = fechaCreacion.toString()
)

fun Usuario.aRespuestaCreado() = RespuestaUsuarioCreado(
    id = id.toString(),
    correo = correo,
    nombre = nombre,
    idioma = idioma,
    rol = rol
)
