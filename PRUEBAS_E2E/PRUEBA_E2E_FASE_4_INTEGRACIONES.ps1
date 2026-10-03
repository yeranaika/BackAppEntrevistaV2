<#
.SYNOPSIS
    Prueba de punta a punta de la Fase 4 (integraciones): servidor real + Postgres + Redis reales.

.DESCRIPTION
    Recorre la caché Redis del mercado (y su invalidación), el catálogo público con sus rutas antiguas,
    la administración del mercado (solo admin), consentimientos, documentos legales, recordatorios,
    billing (códigos y compras de Google Play simuladas), el bloqueo de login y el esquema tras la
    migración 015.
    NO llama a las APIs de empleo (sincronizar cuesta cuota): los cargos se crean con autoGenerateSkills=false.
    NO publica versiones del EULA. Requiere GOOGLE_PLAY_BILLING_MOCK=true para la sección de compras.
    Con -ConLimites prueba el límite por IP de /auth/register (deja la IP bloqueada unos minutos).
    Crea usuarios e2e_fase4_*, un cargo y códigos con label e2e_fase4 y los borra al terminar.

.EXAMPLE
    pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_4_INTEGRACIONES.ps1 -UrlBase http://127.0.0.1:8093
#>
param(
    [string]$UrlBase = 'http://127.0.0.1:8080',
    [string]$ContenedorBd = 'Entrevista_APP',
    [string]$ContenedorRedis = 'Entrevista_Redis',
    [switch]$ConLimites
)

. "$PSScriptRoot/UTILIDAD_E2E.ps1"
Inicializar-E2E -UrlBase $UrlBase -ContenedorBd $ContenedorBd

$CONTRASENA = 'Clave-segura-1'
$sufijo = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$correoAdmin = "e2e_fase4_admin_$sufijo@prueba.local"
$correoAna = "e2e_fase4_ana_$sufijo@prueba.local"
$correoBeto = "e2e_fase4_beto_$sufijo@prueba.local"
$correoBloqueo = "e2e_fase4_bloqueo_$sufijo@prueba.local"
$nombreCargo = "E2E Cargo Fase4 $sufijo"
$ETIQUETA_CODIGOS = 'e2e_fase4'

function Login([string]$Correo) { (Llamar-Api POST '/auth/login' @{ email = $Correo; password = $CONTRASENA }).Json.accessToken }
function Redis([string[]]$Argumentos) { (docker exec $ContenedorRedis redis-cli @Argumentos | Out-String).Trim() }
function IdDe([string]$Correo) { Ejecutar-Sql "select usuario_id from app.usuario where correo = '$Correo'" }

Write-Host "Prueba E2E Fase 4 (integraciones) contra $UrlBase"

try {
    Seccion 'Preparación'
    foreach ($correo in @($correoAdmin, $correoAna, $correoBeto, $correoBloqueo)) {
        Llamar-Api POST '/auth/register' @{ email = $correo; password = $CONTRASENA } | Out-Null
    }
    Ejecutar-Sql "update app.usuario set rol = 'admin' where correo = '$correoAdmin'" | Out-Null
    $admin = Login $correoAdmin
    $ana = Login $correoAna
    $beto = Login $correoBeto
    Probar 'usuarios de prueba listos' { $admin -and $ana -and $beto }

    Seccion 'Salud y esquema (migración 015)'
    $salud = Llamar-Api GET '/health'
    Probar 'GET /health → 200 OK (verifica la BD)' { $salud.Estado -eq 200 -and $salud.Texto -eq 'OK' }
    Probar 'consentimiento ya no tiene la columna extra "alcances"' {
        (Ejecutar-Sql "select count(*) from information_schema.columns where table_schema='app' and table_name='consentimiento' and column_name='alcances'") -eq '0'
    }
    Probar 'suscripcion.token_compra_hash con índice único' {
        (Ejecutar-Sql "select count(*) from pg_indexes where schemaname='app' and indexname='idx_suscripcion_token_compra' and indexdef like '%UNIQUE%'") -eq '1'
    }
    Probar 'fechas con zona horaria (usuario y consentimiento)' {
        (Ejecutar-Sql "select string_agg(data_type, ',') from information_schema.columns where table_schema='app' and ((table_name='usuario' and column_name='fecha_creacion') or (table_name='consentimiento' and column_name='fecha_otorgado'))") -eq 'timestamp with time zone,timestamp with time zone'
    }

    Seccion 'Mercado: administración solo para admin'
    $pedidoCargo = @{ nombre = $nombreCargo; area = 'backend'; descripcion = 'Cargo de prueba'; nivelBase = 'semisenior'; autoGenerateSkills = $false }
    Probar 'sin token → 401' { (Llamar-Api POST '/admin/market/cargos' $pedidoCargo).Estado -eq 401 }
    Probar 'usuario normal → 403' { (Llamar-Api POST '/admin/market/cargos' $pedidoCargo -Token $ana).Estado -eq 403 }
    Probar 'usuario normal no puede sincronizar (consume cuota) → 403' { (Llamar-Api POST '/admin/market/sync-trends' -Token $ana).Estado -eq 403 }

    Seccion 'Mercado: caché Redis'
    $antes = Llamar-Api GET '/api/v1/cargos'
    Probar 'GET /api/v1/cargos → 200 lista' { $antes.Estado -eq 200 -and $antes.Texto.Trim().StartsWith('[') }
    Probar 'queda en Redis la clave mercado:cargos' { (Redis @('EXISTS', 'mercado:cargos')) -eq '1' }
    Probar 'la clave tiene TTL (6 h)' { $ttl = [int](Redis @('TTL', 'mercado:cargos')); $ttl -gt 0 -and $ttl -le 21600 }

    $creado = Llamar-Api POST '/admin/market/cargos' $pedidoCargo -Token $admin
    $cargoId = $creado.Json.cargo.cargoId
    Probar 'admin crea el cargo → 201 sin generar skills' { $creado.Estado -eq 201 -and $cargoId -and $null -eq $creado.Json.generatedSkills }
    Probar 'crear el cargo invalida la caché (antes quedaba desactualizada 6 h)' { (Redis @('EXISTS', 'mercado:cargos')) -eq '0' }
    $despues = Llamar-Api GET '/api/v1/cargos'
    Probar 'el cargo nuevo aparece de inmediato' { ($despues.Json | Where-Object cargoId -eq $cargoId).nombre -eq $nombreCargo }
    Probar 'mismo nombre otra vez → 409 cargo_existente' { (Llamar-Api POST '/admin/market/cargos' $pedidoCargo -Token $admin).Json.error -eq 'cargo_existente' }
    Probar 'nivel inválido → 400 nivel_invalido' {
        (Llamar-Api POST '/admin/market/cargos' @{ nombre = "$nombreCargo X"; area = 'backend'; nivelBase = 'experto'; autoGenerateSkills = $false } -Token $admin).Json.error -eq 'nivel_invalido'
    }

    Seccion 'Mercado: catálogo público y rutas antiguas'
    Probar 'alias /market/cargos responde lo mismo' { @((Llamar-Api GET '/market/cargos').Json).Count -eq @($despues.Json).Count }
    $matriz = Llamar-Api GET "/api/v1/cargos/$cargoId/skills"
    Probar 'matriz del cargo → 200 con el cargo' { $matriz.Estado -eq 200 -and $matriz.Json.cargoId -eq $cargoId }
    Probar 'formato antiguo /market/cargos/{id}/skills → lista' { $l = Llamar-Api GET "/market/cargos/$cargoId/skills"; $l.Estado -eq 200 -and $l.Texto.Trim().StartsWith('[') }
    Probar 'cargo inexistente → 404 cargo_not_found' { (Llamar-Api GET "/api/v1/cargos/$([guid]::NewGuid())/skills").Json.error -eq 'cargo_not_found' }
    Probar 'id que no es UUID → 400' { (Llamar-Api GET '/api/v1/cargos/abc/skills').Estado -eq 400 }
    $tendencias = Llamar-Api GET '/api/v1/skills/trending?categoria=tecnica&limit=5'
    Probar 'skills en tendencia → 200 (máximo 5)' { $tendencias.Estado -eq 200 -and @($tendencias.Json.skills).Count -le 5 }
    Probar 'categoría inválida → 400 categoria_invalida' { (Llamar-Api GET '/api/v1/skills/trending?categoria=dura').Json.error -eq 'categoria_invalida' }
    Probar 'límite fuera de rango → 400 limite_invalido' { (Llamar-Api GET '/api/v1/skills/trending?limit=500').Json.error -eq 'limite_invalido' }
    Probar 'las tendencias quedan en caché' { (Redis @('KEYS', 'mercado:tendencias*')) -ne '' }
    $skills = Llamar-Api GET '/market/skills'
    Probar '/market/skills → 200 lista' { $skills.Estado -eq 200 -and $skills.Texto.Trim().StartsWith('[') }
    if (@($skills.Json).Count -gt 0) {
        Probar 'historial de una skill → 200' { (Llamar-Api GET "/market/skills/$($skills.Json[0].skillId)/tendencias").Estado -eq 200 }
    }
    Probar 'historial de skill inexistente → 404' { (Llamar-Api GET "/market/skills/$([guid]::NewGuid())/tendencias").Estado -eq 404 }

    Seccion 'Consentimiento (Android)'
    $vigente = Llamar-Api GET '/consent/current'
    $version = $vigente.Json.version
    Probar 'GET /consent/current → versión, título y cuerpo' { $vigente.Estado -eq 200 -and $version -and $vigente.Json.body }
    Probar 'sin consentimiento → 204' { (Llamar-Api GET '/me/consent/latest' -Token $ana).Estado -eq 204 }
    $otorgado = Llamar-Api POST '/me/consent' @{ version = $version; alcances = @{ uso_datos = $true; ia_entrenamiento = $true; marketing = $false } } -Token $ana
    Probar 'POST /me/consent → 201' { $otorgado.Estado -eq 201 -and $otorgado.Json.alcances.uso_datos -eq $true }
    $idAna = IdDe $correoAna
    Probar 'en BD: alcances_aceptados y acepta_entrenamiento_ia (antes quedaban vacíos)' {
        (Ejecutar-Sql "select alcances_aceptados::text || '|' || acepta_entrenamiento_ia from app.consentimiento where usuario_id = '$idAna' and fecha_revocado is null") -eq '["ia_entrenamiento", "uso_datos"]|true'
    }
    Probar 'en BD: guarda la IP de origen' { (Ejecutar-Sql "select count(*) from app.consentimiento where usuario_id = '$idAna' and ip_origen is not null") -eq '1' }
    Llamar-Api POST '/me/consent' @{ version = $version; alcances = @{ uso_datos = $true } } -Token $ana | Out-Null
    Probar 'un segundo consentimiento revoca el anterior (solo 1 vigente)' {
        (Ejecutar-Sql "select count(*) || '/' || count(*) filter (where fecha_revocado is null) from app.consentimiento where usuario_id = '$idAna'") -eq '2/1'
    }
    Probar 'GET /me/consent/latest → el vigente' { (Llamar-Api GET '/me/consent/latest' -Token $ana).Json.version -eq $version }
    $inexistente = Llamar-Api POST '/me/consent' @{ version = '9.9.9'; alcances = @{ uso_datos = $true } } -Token $ana
    Probar 'versión inexistente → 404 version_no_encontrada (antes 500 por la FK)' { $inexistente.Estado -eq 404 -and $inexistente.Json.error -eq 'version_no_encontrada' }
    Probar 'revocar → revoked=true' { (Llamar-Api POST '/me/consent/revoke' -Token $ana).Json.revoked -eq $true }
    Probar 'revocar otra vez → 404 consentimiento_no_encontrado' { (Llamar-Api POST '/me/consent/revoke' -Token $ana).Json.error -eq 'consentimiento_no_encontrado' }
    Probar 'sin token → 401' { (Llamar-Api POST '/me/consent' @{ version = $version; alcances = @{ uso_datos = $true } }).Estado -eq 401 }

    Seccion 'Documentos legales'
    Probar 'EULA vigente con contenido' { $e = Llamar-Api GET '/api/v1/legal/eula'; $e.Estado -eq 200 -and $e.Json.type -eq 'eula' -and $e.Json.contentMarkdown }
    Probar 'términos desde docs/legal' { (Llamar-Api GET '/api/v1/legal/terms').Json.contentMarkdown -match 'TÉRMINOS' }
    Probar 'privacidad desde docs/legal' { (Llamar-Api GET '/api/v1/legal/privacy').Json.contentMarkdown -match 'PRIVACIDAD' }
    Probar 'versiones del EULA → exactamente 1 vigente' { @((Llamar-Api GET '/api/v1/legal/versions').Json | Where-Object vigente).Count -eq 1 }
    Probar 'publicar EULA como usuario normal → 403' {
        (Llamar-Api POST '/api/v1/legal/admin/eula' @{ version = '9.9.9'; title = 'x'; body = 'x' } -Token $ana).Estado -eq 403
    }

    Seccion 'Recordatorios'
    $RUTA_RECORDATORIO = '/recordatorios/preferencias'
    Probar 'sin preferencias → 404 recordatorio_no_configurado' { (Llamar-Api GET $RUTA_RECORDATORIO -Token $ana).Json.error -eq 'recordatorio_no_configurado' }
    $guardado = Llamar-Api PUT $RUTA_RECORDATORIO @{ diasSemana = @('DOM', 'LUN', 'MAR', 'MIE', 'JUE', 'VIE', 'SAB'); hora = '20:30'; tipoPractica = 'entrevista'; habilitado = $true } -Token $ana
    Probar 'guarda los 7 días en orden de la semana' { $guardado.Estado -eq 200 -and ($guardado.Json.diasSemana -join ',') -eq 'LUN,MAR,MIE,JUE,VIE,SAB,DOM' }
    Probar 'en BD quedan los códigos y la hora' { (Ejecutar-Sql "select dias_semana || '|' || hora from app.recordatorio_preferencia where usuario_id = '$idAna'") -eq 'LUN,MAR,MIE,JUE,VIE,SAB,DOM|20:30' }
    Probar 'actualizar con nombres completos los normaliza' {
        ((Llamar-Api PUT $RUTA_RECORDATORIO @{ diasSemana = @('lunes', 'Miércoles'); hora = '07:05'; tipoPractica = 'test'; habilitado = $false } -Token $ana).Json.diasSemana -join ',') -eq 'LUN,MIE'
    }
    Probar 'GET devuelve lo último guardado' { $r = (Llamar-Api GET $RUTA_RECORDATORIO -Token $ana).Json; $r.hora -eq '07:05' -and $r.habilitado -eq $false }
    Probar 'día inválido → 400 dia_invalido' { (Llamar-Api PUT $RUTA_RECORDATORIO @{ diasSemana = @('FERIADO'); hora = '20:30'; tipoPractica = 'x' } -Token $ana).Json.error -eq 'dia_invalido' }
    Probar 'hora inválida → 400 hora_invalida' { (Llamar-Api PUT $RUTA_RECORDATORIO @{ diasSemana = @('LUN'); hora = '25:00'; tipoPractica = 'x' } -Token $ana).Json.error -eq 'hora_invalida' }

    Seccion 'Billing: códigos'
    Probar 'estado inicial → is_premium=false' { (Llamar-Api GET '/billing/status' -Token $ana).Json.is_premium -eq $false }
    $pedidoCodigo = @{ days = 30; label = $ETIQUETA_CODIGOS; max_uses = 1; license_type = 'PROM' }
    Probar 'crear código como usuario normal → 403' { (Llamar-Api POST '/billing/admin/codes' $pedidoCodigo -Token $ana).Estado -eq 403 }
    $codigo = Llamar-Api POST '/billing/admin/codes' $pedidoCodigo -Token $admin
    Probar 'admin crea código PROM-XXXXXXXX → 201' { $codigo.Estado -eq 201 -and $codigo.Json.code -cmatch '^PROM-[A-Z2-9]{8}$' }
    Probar 'licencia inválida → 400 licencia_invalida' {
        (Llamar-Api POST '/billing/admin/codes' @{ days = 30; label = $ETIQUETA_CODIGOS; license_type = 'VIP' } -Token $admin).Json.error -eq 'licencia_invalida'
    }
    $canje = Llamar-Api POST '/billing/code/redeem' @{ code = $codigo.Json.code.ToLower() } -Token $ana
    Probar 'canjear (sin importar mayúsculas) → premium interna' { $canje.Estado -eq 200 -and $canje.Json.is_premium -eq $true -and $canje.Json.source -eq 'interna' }
    Probar 'expira en ~30 días' { $dias = ($canje.Json.expires_at - $canje.Json.start_at) / 86400000; $dias -ge 29.9 -and $dias -le 30.1 }
    Probar 'el mismo código por otro usuario → 400 codigo_invalido_o_expirado' {
        (Llamar-Api POST '/billing/code/redeem' @{ code = $codigo.Json.code } -Token $beto).Json.error -eq 'codigo_invalido_o_expirado'
    }
    Probar 'en BD el código quedó con 1 uso' { (Ejecutar-Sql "select usos_realizados from app.codigo_suscripcion where codigo = '$($codigo.Json.code)'") -eq '1' }

    $concurrente = (Llamar-Api POST '/billing/admin/codes' @{ days = 7; label = $ETIQUETA_CODIGOS; max_uses = 2; license_type = 'INST' } -Token $admin).Json.code
    $tokensCanje = @($admin, $beto, $ana, (Login $correoBloqueo))
    $respuestas = Llamar-ApiEnParalelo @($tokensCanje | ForEach-Object { Nueva-Solicitud POST '/billing/code/redeem' @{ code = $concurrente } $_ })
    Probar '4 canjes simultáneos de un código de 2 usos → exactamente 2 éxitos (antes no era atómico)' { @($respuestas | Where-Object Estado -eq 200).Count -eq 2 }
    Probar 'en BD usos_realizados = 2' { (Ejecutar-Sql "select usos_realizados from app.codigo_suscripcion where codigo = '$concurrente'") -eq '2' }

    Seccion 'Billing: Google Play (simulado)'
    $tokenCompra = "e2e-fase4-token-$sufijo"
    $compra = @{ product_id = 'premium_mensual'; purchase_token = $tokenCompra; purchase_time = $sufijo }
    $verificada = Llamar-Api POST '/billing/google/verify' $compra -Token $beto
    if ($verificada.Estado -eq 503) {
        Write-Host '  (Google Play no disponible y GOOGLE_PLAY_BILLING_MOCK no está activo: se omiten las compras)' -ForegroundColor Yellow
    } else {
        Probar 'verificar compra → premium_active' { $verificada.Estado -eq 200 -and $verificada.Json.status -eq 'premium_active' }
        $idBeto = IdDe $correoBeto
        Probar 'en BD se guarda solo el hash del token, nunca el token' {
            (Ejecutar-Sql "select count(*) from app.suscripcion where usuario_id = '$idBeto' and token_compra_hash is not null and token_compra_hash <> '$tokenCompra'") -ne '0'
        }
        Probar 'el mismo dueño puede reverificar (reinstalación) → 200' { (Llamar-Api POST '/billing/google/verify' $compra -Token $beto).Estado -eq 200 }
        $robada = Llamar-Api POST '/billing/google/verify' $compra -Token $ana
        Probar 'el mismo pago en otra cuenta → 409 compra_ya_registrada' { $robada.Estado -eq 409 -and $robada.Json.error -eq 'compra_ya_registrada' }
        Probar 'estado de beto → premium' { (Llamar-Api GET '/billing/status' -Token $beto).Json.is_premium -eq $true }
    }
    Probar 'compra sin token de sesión → 401' { (Llamar-Api POST '/billing/google/verify' $compra).Estado -eq 401 }

    Seccion 'Bloqueo de login (compartido vía Redis)'
    $malos = 1..5 | ForEach-Object { (Llamar-Api POST '/auth/login' @{ email = $correoBloqueo; password = 'incorrecta' }).Estado }
    Probar '5 intentos fallidos → 401' { @($malos | Where-Object { $_ -eq 401 }).Count -eq 5 }
    $bloqueado = Llamar-Api POST '/auth/login' @{ email = $correoBloqueo; password = $CONTRASENA }
    Probar 'el 6º, aun con la contraseña correcta → 429 demasiados_intentos' { $bloqueado.Estado -eq 429 -and $bloqueado.Json.error -eq 'demasiados_intentos' }
    Probar 'el contador vive en Redis con TTL' { [int](Redis @('TTL', "login:fallos:$correoBloqueo")) -gt 0 }
    Redis @('DEL', "login:fallos:$correoBloqueo") | Out-Null
    Probar 'al expirar el bloqueo vuelve a entrar' { (Llamar-Api POST '/auth/login' @{ email = $correoBloqueo; password = $CONTRASENA }).Estado -eq 200 }

    if ($ConLimites) {
        Seccion 'Límite por IP de /auth/register (-ConLimites)'
        $estados = 1..40 | ForEach-Object { (Llamar-Api POST '/auth/register' @{ email = "e2e_fase4_limite_$($sufijo)_$_@prueba.local"; password = $CONTRASENA }).Estado }
        Probar 'tras el límite responde 429' { $estados -contains 429 }
        Probar 'con el formato estándar demasiadas_solicitudes' {
            (Llamar-Api POST '/auth/register' @{ email = "e2e_fase4_limite_extra_$sufijo@prueba.local"; password = $CONTRASENA }).Json.error -eq 'demasiadas_solicitudes'
        }
    }
}
finally {
    Seccion 'Limpieza'
    Ejecutar-Sql "delete from app.cargo where nombre like 'E2E Cargo Fase4 %'" | Out-Null
    $condicion = "usuario_id in (select usuario_id from app.usuario where correo like 'e2e\_fase4\_%@prueba.local')"
    Ejecutar-Sql "delete from app.suscripcion where $condicion" | Out-Null
    $codigos = Ejecutar-Sql "delete from app.codigo_suscripcion where label = '$ETIQUETA_CODIGOS'"
    Ejecutar-Sql "delete from app.recordatorio_preferencia where $condicion" | Out-Null
    Ejecutar-Sql "delete from app.consentimiento where $condicion" | Out-Null
    $usuarios = Ejecutar-Sql "delete from app.usuario where correo like 'e2e\_fase4\_%@prueba.local'"
    foreach ($patron in @('login:fallos:e2e_fase4_*', 'mercado:*')) {
        docker exec $ContenedorRedis redis-cli --scan --pattern $patron | ForEach-Object { docker exec $ContenedorRedis redis-cli DEL $_ | Out-Null }
    }
    Write-Host "  usuarios: $usuarios · códigos: $codigos · cargo, consentimientos, recordatorios, suscripciones y claves Redis de prueba eliminados"
}

Mostrar-Resumen
