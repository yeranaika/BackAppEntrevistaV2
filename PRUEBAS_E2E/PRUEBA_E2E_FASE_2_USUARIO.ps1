<#
.SYNOPSIS
    Prueba de punta a punta de la Fase 2 (usuarios): servidor real + Postgres real.

.DESCRIPTION
    Recorre registro (con el JSON exacto de Android), /me, perfil, objetivo, onboarding,
    recuperación y cambio de contraseña, administración de usuarios y borrado de cuenta.
    No envía correos: el código de recuperación se inserta directo en la BD.
    Crea usuarios e2e_fase2_*@prueba.local y los borra al terminar.
    Requiere la migración migrations/014_password_reset_intentos.sql aplicada.

.EXAMPLE
    pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_2_USUARIO.ps1 -UrlBase http://127.0.0.1:8093
#>
param(
    [string]$UrlBase = 'http://127.0.0.1:8080',
    [string]$ContenedorBd = 'Entrevista_APP'
)

. "$PSScriptRoot/UTILIDAD_E2E.ps1"
Inicializar-E2E -UrlBase $UrlBase -ContenedorBd $ContenedorBd

$CONTRASENA = 'Clave-segura-1'
$sufijo = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$correo = "e2e_fase2_$sufijo@prueba.local"
$correoAdmin = "e2e_fase2_admin_$sufijo@prueba.local"

function Login([string]$Correo, [string]$Contrasena = $CONTRASENA) {
    Llamar-Api POST '/auth/login' @{ email = $Correo; password = $Contrasena }
}

function Sql-Valor([string]$Sql) { Ejecutar-Sql $Sql }

function Insertar-Codigo([string]$Correo, [string]$Codigo) {
    # Mismo efecto que /auth/forgot-password, sin enviar correo.
    Ejecutar-Sql "update app.password_reset set used = true where usuario_id = (select usuario_id from app.usuario where correo = '$Correo')" | Out-Null
    Ejecutar-Sql ("insert into app.password_reset (token, usuario_id, code, issued_at, expires_at, used, intentos_fallidos) " +
        "select gen_random_uuid(), usuario_id, '$Codigo', now(), now() + interval '15 minutes', false, 0 " +
        "from app.usuario where correo = '$Correo'") | Out-Null
}

function Restablecer([string]$Codigo, [string]$Nueva = 'Clave-nueva-1') {
    Llamar-Api POST '/auth/reset-password' @{ correo = $correo; codigo = $Codigo; nuevaContrasena = $Nueva }
}

Write-Host "Prueba E2E Fase 2 (usuarios) contra $UrlBase"

try {
    Seccion 'Registro'
    # JSON idéntico al que arma RegisterViewModel de Android (campos de perfil vacíos).
    $cuerpoAndroid = @{
        email = $correo; password = $CONTRASENA; nombre = 'Ana'; idioma = 'es'
        telefono = $null; fechaNacimiento = $null; genero = $null
        nivelExperiencia = ''; area = ''; pais = ''; notaObjetivos = ''
        flagsAccesibilidad = @{ tts = $false; altoContraste = $true }
    }
    $registro = Llamar-Api POST '/auth/register' $cuerpoAndroid
    Probar 'el registro con el JSON de Android responde 201 (antes daba 500)' { $registro.Estado -eq 201 }
    Probar 'entrega accessToken y refreshToken' { $registro.Json.accessToken -and $registro.Json.refreshToken }
    Probar 'crea el perfil con las preferencias de accesibilidad' {
        (Sql-Valor "select p.flags_accesibilidad->>'altoContraste' from app.perfil_usuario p join app.usuario u using (usuario_id) where u.correo = '$correo'") -eq 'true'
    }

    $duplicado = Llamar-Api POST '/auth/register' $cuerpoAndroid
    Probar 'correo repetido → 409 email_in_use' { $duplicado.Estado -eq 409 -and $duplicado.Json.error -eq 'email_in_use' }
    Probar 'el error del registro trae solo el campo error (Android lo lee con Json estricto)' {
        $campos = @($duplicado.Json.PSObject.Properties.Name)
        $campos.Count -eq 1 -and $campos[0] -eq 'error'
    }

    $debil = Llamar-Api POST '/auth/register' @{ email = "e2e_fase2_debil_$sufijo@prueba.local"; password = 'corta' }
    Probar 'contraseña débil → 422 weak_password' { $debil.Estado -eq 422 -and $debil.Json.error -eq 'weak_password' }

    $nivelMalo = Llamar-Api POST '/auth/register' @{ email = "e2e_fase2_nivel_$sufijo@prueba.local"; password = $CONTRASENA; nivelExperiencia = 'experto' }
    Probar 'nivel inválido → 422 y no deja la cuenta creada a medias' {
        $nivelMalo.Estado -eq 422 -and (Sql-Valor "select count(*) from app.usuario where correo = 'e2e_fase2_nivel_$sufijo@prueba.local'") -eq '0'
    }

    $token = (Login $correo).Json.accessToken

    Seccion 'Cuenta (/me)'
    $me = Llamar-Api GET '/me' -Token $token
    Probar 'GET /me devuelve correo, nombre e idioma' { $me.Estado -eq 200 -and $me.Json.email -eq $correo -and $me.Json.nombre -eq 'Ana' -and $me.Json.idioma -eq 'es' }
    $actualizar = Llamar-Api PUT '/me' @{ nombre = 'Ana María'; telefono = '+56912345678'; fechaNacimiento = '1995-05-20'; genero = 'femenino' } -Token $token
    Probar 'PUT /me actualiza los datos' { $actualizar.Estado -eq 200 -and (Llamar-Api GET '/me' -Token $token).Json.telefono -eq '+56912345678' }
    Probar 'PUT /me con teléfono vacío lo borra' {
        (Llamar-Api PUT '/me' @{ telefono = '' } -Token $token).Estado -eq 200 -and -not (Llamar-Api GET '/me' -Token $token).Json.telefono
    }
    $idioma = Llamar-Api PUT '/me' @{ idioma = 'klingon' } -Token $token
    Probar 'idioma inválido → 400 idioma_invalido' { $idioma.Estado -eq 400 -and $idioma.Json.error -eq 'idioma_invalido' }
    $vacio = Llamar-Api PUT '/me' @{} -Token $token
    Probar 'sin cambios → 400 nothing_to_update' { $vacio.Json.error -eq 'nothing_to_update' }

    Seccion 'Perfil (/me/perfil)'
    $perfil = Llamar-Api PUT '/me/perfil' @{ nivelExperiencia = 'mid'; area = 'Analista'; pais = 'cl' } -Token $token
    Probar 'guardar nivel "mid" responde 200 (antes violaba el CHECK de la BD)' { $perfil.Estado -eq 200 }
    Probar 'en la BD queda "semisenior", el valor que acepta el CHECK' {
        (Sql-Valor "select p.nivel_experiencia from app.perfil_usuario p join app.usuario u using (usuario_id) where u.correo = '$correo'") -eq 'semisenior'
    }
    $leido = Llamar-Api GET '/me/perfil' -Token $token
    Probar 'la app lo recibe como "mid" y el país en mayúsculas' { $leido.Json.nivelExperiencia -eq 'mid' -and $leido.Json.pais -eq 'CL' }
    $areaMala = Llamar-Api PUT '/me/perfil' @{ area = 'Astronauta' } -Token $token
    Probar 'área inválida → 400 area_invalida' { $areaMala.Estado -eq 400 -and $areaMala.Json.error -eq 'area_invalida' }

    Seccion 'Objetivo y onboarding'
    $onbAndroid = Llamar-Api PUT '/perfil/objetivo' @{ area = 'Desarollador'; metaCargo = 'Android Developer'; nivel = 'jr' } -Token $token
    Probar 'PUT /perfil/objetivo (onboarding Android) → {"status":"ok"}' { $onbAndroid.Estado -eq 200 -and $onbAndroid.Json.status -eq 'ok' }
    $estado = Llamar-Api GET '/onboarding/status' -Token $token
    Probar 'onboarding completo con nivel "jr"' { $estado.Json.completed -eq $true -and $estado.Json.data.nivelExperiencia -eq 'jr' }
    Probar 'GET /me muestra el cargo meta' { (Llamar-Api GET '/me' -Token $token).Json.meta -eq 'Android Developer' }

    $objetivo = Llamar-Api PUT '/me/objetivo' @{ nombreCargo = 'Data Analyst'; sector = 'TI' } -Token $token
    Probar 'PUT /me/objetivo reemplaza el cargo meta' { $objetivo.Estado -eq 200 -and (Llamar-Api GET '/me/objetivo' -Token $token).Json.nombreCargo -eq 'Data Analyst' }
    Probar 'el objetivo anterior queda en el historial (inactivo)' {
        [int](Sql-Valor "select count(*) from app.objetivo_carrera o join app.usuario u using (usuario_id) where u.correo = '$correo'") -ge 2
    }
    Probar 'DELETE /me/objetivo lo desactiva' {
        (Llamar-Api DELETE '/me/objetivo' -Token $token).Estado -eq 200 -and (Llamar-Api GET '/me/objetivo' -Token $token).Estado -eq 404
    }
    $onboarding = Llamar-Api POST '/onboarding' @{ area = 'TI'; nivelExperiencia = 'Semi Senior'; nombreCargo = 'Backend Developer'; descripcionObjetivo = 'Primer trabajo' } -Token $token
    Probar 'POST /onboarding guarda y responde success' { $onboarding.Json.success -eq $true -and $onboarding.Json.data.nivelExperiencia -eq 'mid' }

    Seccion 'Recuperación de contraseña'
    $olvido = Llamar-Api POST '/auth/forgot-password' @{ correo = "nadie_$sufijo@prueba.local" }
    Probar 'forgot-password con correo no registrado responde 200 igual (no revela si existe)' { $olvido.Estado -eq 200 -and $olvido.Json.message }

    $sesionPrevia = (Login $correo).Json.refreshToken
    Insertar-Codigo $correo '123456'
    $intentos = 1..4 | ForEach-Object { Restablecer '000000' }
    Probar '4 códigos incorrectos → 400 con el mensaje que muestra Android' {
        @($intentos | Where-Object { $_.Estado -eq 400 -and $_.Json.message -eq 'Código inválido o expirado' }).Count -eq 4
    }
    $quinto = Restablecer '000000'
    Probar 'el 5.º intento incorrecto → 429 demasiados_intentos' { $quinto.Estado -eq 429 -and $quinto.Json.error -eq 'demasiados_intentos' }
    Probar 'tras bloquearse, ni el código correcto sirve' { (Restablecer '123456').Estado -eq 400 }

    Insertar-Codigo $correo '654321'
    Probar 'con un código nuevo correcto se cambia la contraseña' { (Restablecer '654321').Estado -eq 200 }
    Probar 'se puede entrar con la contraseña nueva' { (Login $correo 'Clave-nueva-1').Estado -eq 200 }
    Probar 'las sesiones abiertas antes del cambio quedaron cerradas' {
        (Llamar-Api POST '/auth/refresh' @{ refreshToken = $sesionPrevia }).Estado -eq 401
    }

    Seccion 'Cambio de contraseña desde el perfil'
    $token = (Login $correo 'Clave-nueva-1').Json.accessToken
    $malActual = Llamar-Api POST '/auth/change-password' @{ contrasenaActual = 'cualquiera'; nuevaContrasena = 'Clave-otra-1' } -Token $token
    Probar 'contraseña actual incorrecta → 400 (antes se aceptaba cualquiera)' {
        $malActual.Estado -eq 400 -and $malActual.Json.message -eq 'La contraseña actual es incorrecta'
    }
    $cambio = Llamar-Api POST '/auth/change-password' @{ contrasenaActual = 'Clave-nueva-1'; nuevaContrasena = $CONTRASENA } -Token $token
    Probar 'con la contraseña actual correcta → 200' { $cambio.Estado -eq 200 }
    Probar 'la contraseña se guarda con Argon2id' {
        (Sql-Valor "select left(contrasena_hash, 9) from app.usuario where correo = '$correo'") -eq '$argon2id'
    }

    Seccion 'Administración de usuarios'
    Llamar-Api POST '/auth/register' @{ email = $correoAdmin; password = $CONTRASENA } | Out-Null
    Ejecutar-Sql "update app.usuario set rol = 'admin' where correo = '$correoAdmin'" | Out-Null
    $tokenAdmin = (Login $correoAdmin).Json.accessToken
    Probar 'un usuario normal recibe 403 en /admin/usuarios' { (Llamar-Api GET '/admin/usuarios' -Token $token).Estado -eq 403 }
    $lista = Llamar-Api GET '/admin/usuarios' -Token $tokenAdmin
    Probar 'el admin lista usuarios' { $lista.Estado -eq 200 -and @($lista.Json | Where-Object correo -eq $correo).Count -eq 1 }

    $creado = Llamar-Api POST '/admin/usuarios' @{ correo = "E2E_fase2_creado_$sufijo@prueba.local"; contrasena = $CONTRASENA } -Token $tokenAdmin
    Probar 'el admin crea un usuario (correo normalizado)' { $creado.Estado -eq 201 -and $creado.Json.correo -eq "e2e_fase2_creado_$sufijo@prueba.local" }
    $creadoLegado = Llamar-Api POST '/admin/users' @{ correo = "e2e_fase2_legado_$sufijo@prueba.local"; contrasena = $CONTRASENA } -Token $tokenAdmin
    Probar 'la ruta antigua /admin/users sigue funcionando' { $creadoLegado.Estado -eq 201 }

    $idAna = $me.Json.id
    $sesionAna = (Login $correo).Json.refreshToken
    Probar 'desactivar usuario → 200' { (Llamar-Api DELETE "/admin/usuarios/$idAna" -Token $tokenAdmin).Estado -eq 200 }
    Probar 'el usuario desactivado no puede entrar (403)' { (Login $correo).Estado -eq 403 }
    Probar 'y su sesión abierta ya no se renueva' { (Llamar-Api POST '/auth/refresh' @{ refreshToken = $sesionAna }).Estado -ne 200 }
    Probar 'reactivar → puede volver a entrar' {
        (Llamar-Api PATCH "/admin/usuarios/$idAna/activar" -Token $tokenAdmin).Estado -eq 200 -and (Login $correo).Estado -eq 200
    }
    Probar 'el admin restablece la contraseña del usuario' {
        (Llamar-Api PATCH "/admin/usuarios/$idAna/password" @{ nuevaContrasena = 'Clave-admin-1' } -Token $tokenAdmin).Estado -eq 200 -and
            (Login $correo 'Clave-admin-1').Estado -eq 200
    }
    $idAdmin = (Llamar-Api GET '/me' -Token $tokenAdmin).Json.id
    $auto = Llamar-Api DELETE "/admin/usuarios/$idAdmin" -Token $tokenAdmin
    Probar 'un admin no puede desactivarse a sí mismo' { $auto.Estado -eq 400 -and $auto.Json.error -eq 'no_puede_desactivarse' }
    Probar 'UUID inválido en la ruta → 400 id_invalido' {
        (Llamar-Api PATCH '/admin/usuarios/no-es-uuid/activar' -Token $tokenAdmin).Json.error -eq 'id_invalido'
    }

    Seccion 'Borrado de cuenta'
    $token = (Login $correo 'Clave-admin-1').Json.accessToken
    $sinConfirmar = Llamar-Api DELETE '/cuenta' @{ confirmar = 'si' } -Token $token
    Probar 'sin escribir "eliminar" → 400 must_type_eliminar' { $sinConfirmar.Json.error -eq 'must_type_eliminar' }
    $borrado = Llamar-Api DELETE '/cuenta' @{ confirmar = 'eliminar' } -Token $token
    Probar 'con confirmación → 200' { $borrado.Estado -eq 200 -and $borrado.Json.message }
    Probar 'el usuario y su perfil desaparecen de la BD' {
        (Sql-Valor "select count(*) from app.usuario where correo = '$correo'") -eq '0' -and
            (Sql-Valor "select count(*) from app.perfil_usuario where usuario_id = '$idAna'") -eq '0'
    }
    Probar 'GET /me con el token anterior → 404' { (Llamar-Api GET '/me' -Token $token).Estado -eq 404 }
}
finally {
    Seccion 'Limpieza'
    $borrados = Ejecutar-Sql "delete from app.usuario where correo like 'e2e\_fase2\_%@prueba.local'"
    Write-Host "  usuarios de prueba eliminados: $borrados"
}

Mostrar-Resumen
