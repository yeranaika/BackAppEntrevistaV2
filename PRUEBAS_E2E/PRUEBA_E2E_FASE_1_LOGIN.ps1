<#
.SYNOPSIS
    Prueba de punta a punta de la Fase 1 (login): servidor real + Postgres real.

.DESCRIPTION
    Recorre login, Google, refresh, logout, rol admin, cuenta inactiva y concurrencia
    contra el backend levantado. Crea usuarios e2e_fase1_*@prueba.local y los borra al terminar.
    Termina con código 0 si todo pasa, 1 si algo falla y 2 si el backend no responde.

.EXAMPLE
    pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_1_LOGIN.ps1
    pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_1_LOGIN.ps1 -UrlBase http://127.0.0.1:8093
#>
param(
    [string]$UrlBase = 'http://127.0.0.1:8080',
    [string]$ContenedorBd = 'Entrevista_APP'
)

. "$PSScriptRoot/UTILIDAD_E2E.ps1"
Inicializar-E2E -UrlBase $UrlBase -ContenedorBd $ContenedorBd

$CONTRASENA = 'Clave-segura-1'
$correo = "e2e_fase1_$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@prueba.local"

function Login([string]$Correo = $correo, [string]$Contrasena = $CONTRASENA) {
    Llamar-Api POST '/auth/login' @{ email = $Correo; password = $Contrasena }
}

function Refresh([string]$Token) {
    Llamar-Api POST '/auth/refresh' @{ refreshToken = $Token }
}

Write-Host "Prueba E2E Fase 1 (login) contra $UrlBase"

try {
    Seccion 'Preparación'
    $registro = Llamar-Api POST '/auth/register' @{ email = $correo; password = $CONTRASENA }
    Probar 'registro de usuario de prueba responde 201' { $registro.Estado -eq 201 }

    Seccion 'Login con correo y contraseña'
    $login = Login
    Probar 'login correcto responde 200' { $login.Estado -eq 200 }
    Probar 'entrega accessToken JWT con rol user' { (Leer-ClaimJwt $login.Json.accessToken 'role') -eq 'user' }
    Probar 'el access token vive 15 minutos' {
        ((Leer-ClaimJwt $login.Json.accessToken 'exp') - (Leer-ClaimJwt $login.Json.accessToken 'iat')) -eq 900
    }
    Probar 'entrega refreshToken opaco de 43 caracteres' { $login.Json.refreshToken.Length -eq 43 }
    Probar 'registra la fecha de último login' {
        (Ejecutar-Sql "select fecha_ultimo_login is not null from app.usuario where correo = '$correo'") -eq 't'
    }

    $mala = Login -Contrasena 'Otra-clave-1'
    Probar 'contraseña incorrecta responde 401 bad_credentials' { $mala.Estado -eq 401 -and $mala.Json.error -eq 'bad_credentials' }

    $inexistente = Login -Correo 'nadie_e2e@prueba.local'
    Probar 'correo inexistente responde igual: 401 bad_credentials' {
        $inexistente.Estado -eq 401 -and $inexistente.Json.error -eq 'bad_credentials'
    }

    $mayusculas = Login -Correo "  $($correo.ToUpper())  "
    Probar 'el correo se normaliza (espacios y mayúsculas)' { $mayusculas.Estado -eq 200 }

    $sinCampo = Llamar-Api POST '/auth/login' @{ email = $correo }
    Probar 'falta un campo → 400 invalid_json' { $sinCampo.Estado -eq 400 -and $sinCampo.Json.error -eq 'invalid_json' }

    $malformado = Llamar-Api POST '/auth/login' -CuerpoCrudo '{ esto no es json'
    Probar 'JSON malformado → 400 invalid_json' { $malformado.Estado -eq 400 -and $malformado.Json.error -eq 'invalid_json' }

    Seccion 'Access token en rutas protegidas'
    $me = Llamar-Api GET '/me' -Token $login.Json.accessToken
    Probar 'GET /me con token responde 200 y el correo del usuario' { $me.Estado -eq 200 -and $me.Json.email -eq $correo }
    Probar 'GET /me sin token responde 401' { (Llamar-Api GET '/me').Estado -eq 401 }
    Probar 'GET /me con token alterado responde 401' { (Llamar-Api GET '/me' -Token "$($login.Json.accessToken)x").Estado -eq 401 }
    $adminNoPermitido = Llamar-Api POST '/admin/market/sync-trends' -Token $login.Json.accessToken
    Probar 'usuario normal en ruta admin responde 403 admin_required' {
        $adminNoPermitido.Estado -eq 403 -and $adminNoPermitido.Json.error -eq 'admin_required'
    }

    Seccion 'Refresh token: rotación y reutilización'
    $original = (Login).Json.refreshToken
    $rotado = Refresh $original
    Probar 'refresh responde 200 con un par nuevo' {
        $rotado.Estado -eq 200 -and $rotado.Json.refreshToken -ne $original -and $rotado.Json.accessToken
    }
    $reuso = Refresh $original
    Probar 'reutilizar el refresh anterior responde 401 invalid_refresh' {
        $reuso.Estado -eq 401 -and $reuso.Json.error -eq 'invalid_refresh'
    }
    Probar 'tras la reutilización también se revoca el refresh más nuevo' { (Refresh $rotado.Json.refreshToken).Estado -eq 401 }

    $vacio = Refresh '   '
    Probar 'refresh vacío responde 400 missing_refresh' { $vacio.Estado -eq 400 -and $vacio.Json.error -eq 'missing_refresh' }
    Probar 'refresh desconocido responde 401' { (Refresh 'token-que-no-existe').Estado -eq 401 }

    Seccion 'Refresh simultáneo'
    $compartido = (Login).Json.refreshToken
    $solicitudes = 1..2 | ForEach-Object { Nueva-Solicitud POST '/auth/refresh' @{ refreshToken = $compartido } }
    $paralelas = Llamar-ApiEnParalelo $solicitudes
    $ganadoras = @($paralelas | Where-Object Estado -eq 200).Count
    Probar "de 2 refresh simultáneos con el mismo token gana solo 1 (estados: $($paralelas.Estado -join ', '))" { $ganadoras -eq 1 }

    Seccion 'Logout'
    $sesion = (Login).Json.refreshToken
    Probar 'logout responde 200' { (Llamar-Api POST '/auth/logout' @{ refreshToken = $sesion }).Estado -eq 200 }
    Probar 'logout repetido sigue respondiendo 200' { (Llamar-Api POST '/auth/logout' @{ refreshToken = $sesion }).Estado -eq 200 }
    Probar 'el refresh cerrado ya no sirve' { (Refresh $sesion).Estado -eq 401 }

    Seccion 'Rol admin'
    Ejecutar-Sql "update app.usuario set rol = 'admin' where correo = '$correo'" | Out-Null
    $loginAdmin = Login
    Probar 'login de admin firma rol admin' { (Leer-ClaimJwt $loginAdmin.Json.accessToken 'role') -eq 'admin' }
    $refreshAdmin = Refresh $loginAdmin.Json.refreshToken
    Probar 'el rol admin se conserva al renovar' { (Leer-ClaimJwt $refreshAdmin.Json.accessToken 'role') -eq 'admin' }
    Ejecutar-Sql "update app.usuario set rol = 'user' where correo = '$correo'" | Out-Null
    $refreshDegradado = Refresh $refreshAdmin.Json.refreshToken
    Probar 'si se quita el rol admin, el siguiente refresh firma rol user' {
        (Leer-ClaimJwt $refreshDegradado.Json.accessToken 'role') -eq 'user'
    }

    Seccion 'Cuenta inactiva'
    $sesionPrevia = (Login).Json.refreshToken
    Ejecutar-Sql "update app.usuario set estado = 'inactivo' where correo = '$correo'" | Out-Null
    $inactivaOk = Login
    Probar 'con contraseña correcta responde 403 inactive_user' {
        $inactivaOk.Estado -eq 403 -and $inactivaOk.Json.error -eq 'inactive_user'
    }
    $inactivaMala = Login -Contrasena 'Otra-clave-1'
    Probar 'con contraseña incorrecta no revela el estado: 401 bad_credentials' {
        $inactivaMala.Estado -eq 401 -and $inactivaMala.Json.error -eq 'bad_credentials'
    }
    $refreshInactiva = Refresh $sesionPrevia
    Probar 'una sesión abierta antes de desactivar no puede renovarse: 403' { $refreshInactiva.Estado -eq 403 }

    Seccion 'Google'
    $google = Llamar-Api POST '/auth/google' @{ idToken = 'no-es-un-token' }
    Probar 'idToken inválido responde 401 invalid_google_token' {
        $google.Estado -eq 401 -and $google.Json.error -eq 'invalid_google_token'
    }
    $inicioWeb = Llamar-Api GET '/auth/google/start'
    Probar 'GET /auth/google/start redirige a accounts.google.com' {
        $inicioWeb.Estado -eq 302 -and $inicioWeb.Ubicacion.Host -eq 'accounts.google.com'
    }

    Seccion 'Formato de errores'
    Probar 'los errores traen código y mensaje legible' { $mala.Json.error -and $mala.Json.mensaje }
    Probar 'ningún error expone detalles internos' {
        -not (($mala.Texto + $malformado.Texto + $reuso.Texto) -match 'Exception|at [a-z]+\.')
    }
}
finally {
    Seccion 'Limpieza'
    $borrados = Ejecutar-Sql "delete from app.usuario where correo like 'e2e\_fase1\_%@prueba.local'"
    Write-Host "  usuarios de prueba eliminados: $borrados"
}

Mostrar-Resumen
