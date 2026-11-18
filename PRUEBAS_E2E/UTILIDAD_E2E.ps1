# Utilidades compartidas por las pruebas de punta a punta (E2E).
# Se cargan con:  . "$PSScriptRoot/UTILIDAD_E2E.ps1"
# Requieren PowerShell 7+, el backend corriendo y el contenedor de Postgres de src/DB/docker-compose.yml.
# Sin Docker: definir E2E_PSQL con la ruta a psql.exe (ej. $env:E2E_PSQL = 'C:\ruta\pgsql\bin\psql.exe'); la fase 4
# además necesita el contenedor de Redis para sus pruebas del límite por IP.

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$script:Aprobadas = 0
$script:Fallidas = 0

$manejador = [System.Net.Http.HttpClientHandler]::new()
$manejador.AllowAutoRedirect = $false   # para poder verificar redirecciones (ej: OAuth de Google)
$script:ClienteHttp = [System.Net.Http.HttpClient]::new($manejador)
$script:ClienteHttp.Timeout = [TimeSpan]::FromSeconds(30)

function Inicializar-E2E([string]$UrlBase, [string]$ContenedorBd) {
    $script:UrlBase = $UrlBase.TrimEnd('/')
    $script:ContenedorBd = $ContenedorBd

    # Usuario y base de datos se leen del .env del proyecto, igual que el backend.
    $archivoEnv = Join-Path $PSScriptRoot '..' '.env'
    $variables = @{}
    Get-Content $archivoEnv | Where-Object { $_ -match '^\s*([A-Z_]+)\s*=\s*(.*)$' } |
        ForEach-Object { $variables[$Matches[1]] = $Matches[2].Trim() }
    $script:UsuarioBd = $variables['DB_USER']
    $script:ClaveBd = $variables['DB_PASS']
    # jdbc:postgresql://host[:puerto]/bd[?params] o la forma corta jdbc:postgresql:bd (localhost:5432).
    $url = [string]$variables['DB_URL']
    $script:NombreBd = ($url -replace '^jdbc:postgresql:(//[^/]*/)?', '') -replace '\?.*$', ''
    $script:HostBd = 'localhost'
    $script:PuertoBd = '5432'
    if ($url -match '^jdbc:postgresql://') {
        # Host y puerto, para usar un psql local en vez de Docker (E2E_PSQL).
        if ($url -match '^jdbc:postgresql://([^:/\[\],]+)(?::(\d+))?/') {
            $script:HostBd = $Matches[1]
            if ($Matches[2]) { $script:PuertoBd = $Matches[2] }
        } elseif ($env:E2E_PSQL) {
            throw "DB_URL no soportado con E2E_PSQL (IPv6 o varios hosts): $url"
        }
    }

    try {
        $salud = Llamar-Api GET '/health'
        if ($salud.Estado -ne 200) { throw "health respondió $($salud.Estado)" }
    } catch {
        Write-Host "No se pudo contactar el backend en $script:UrlBase ($($_.Exception.Message))." -ForegroundColor Yellow
        Write-Host 'Levántalo primero, por ejemplo:  ./gradlew run' -ForegroundColor Yellow
        exit 2
    }
}

function Nueva-Solicitud([string]$Metodo, [string]$Ruta, $Cuerpo = $null, [string]$Token = $null, [string]$CuerpoCrudo = $null) {
    $solicitud = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Metodo), "$script:UrlBase$Ruta")
    if ($Token) {
        $solicitud.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
    }
    $texto = if ($PSBoundParameters.ContainsKey('CuerpoCrudo')) { $CuerpoCrudo }
             elseif ($null -ne $Cuerpo) { $Cuerpo | ConvertTo-Json -Compress -Depth 10 }
             else { $null }
    if ($null -ne $texto) {
        $solicitud.Content = [System.Net.Http.StringContent]::new($texto, [Text.Encoding]::UTF8, 'application/json')
    }
    return $solicitud
}

function Convertir-Respuesta([System.Net.Http.HttpResponseMessage]$Respuesta) {
    $texto = $Respuesta.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $json = $null
    try { $json = $texto | ConvertFrom-Json } catch { }
    [pscustomobject]@{
        Estado    = [int]$Respuesta.StatusCode
        Json      = $json
        Texto     = $texto
        Ubicacion = $Respuesta.Headers.Location
    }
}

<# Llamada HTTP. -CuerpoCrudo envía el texto tal cual (para probar JSON malformado). #>
function Llamar-Api([string]$Metodo, [string]$Ruta, $Cuerpo = $null, [string]$Token = $null, [string]$CuerpoCrudo) {
    $parametros = @{ Metodo = $Metodo; Ruta = $Ruta; Cuerpo = $Cuerpo; Token = $Token }
    if ($PSBoundParameters.ContainsKey('CuerpoCrudo')) { $parametros['CuerpoCrudo'] = $CuerpoCrudo }
    $solicitud = Nueva-Solicitud @parametros
    Convertir-Respuesta ($script:ClienteHttp.SendAsync($solicitud).GetAwaiter().GetResult())
}

<# Envía varias solicitudes al mismo tiempo y devuelve sus respuestas. #>
function Llamar-ApiEnParalelo([System.Net.Http.HttpRequestMessage[]]$Solicitudes) {
    $tareas = $Solicitudes | ForEach-Object { $script:ClienteHttp.SendAsync($_) }
    [System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]$tareas)
    $tareas | ForEach-Object { Convertir-Respuesta $_.Result }
}

<# SQL directo a la BD. Por defecto con `docker exec` al contenedor; si la variable de entorno E2E_PSQL apunta a un
   psql.exe (PostgreSQL sin Docker), usa ese psql con el host, puerto y clave del .env. #>
function Ejecutar-Sql([string]$Sql) {
    $salida = if ($env:E2E_PSQL) {
        # El SQL va por la entrada estándar en UTF-8: como argumento, psql.exe lo recibiría en la página ANSI de
        # Windows y una 'é' llegaría a Postgres como un byte UTF-8 inválido. Clave y codificación solo para esta llamada.
        $claveAnterior = $env:PGPASSWORD
        $codificacionAnterior = $env:PGCLIENTENCODING
        $OutputEncoding = [Text.UTF8Encoding]::new($false)
        try {
            $env:PGPASSWORD = $script:ClaveBd
            $env:PGCLIENTENCODING = 'UTF8'
            $Sql | & $env:E2E_PSQL -X -v ON_ERROR_STOP=1 -h $script:HostBd -p $script:PuertoBd -U $script:UsuarioBd -d $script:NombreBd -tA 2>&1
        } finally {
            $env:PGPASSWORD = $claveAnterior
            $env:PGCLIENTENCODING = $codificacionAnterior
        }
    } else {
        docker exec $script:ContenedorBd psql -U $script:UsuarioBd -d $script:NombreBd -tAc $Sql 2>&1
    }
    if ($LASTEXITCODE -ne 0) { throw "SQL falló: $salida" }
    return ($salida | Out-String).Trim()
}

function Leer-ClaimJwt([string]$Token, [string]$Claim) {
    $carga = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    switch ($carga.Length % 4) { 2 { $carga += '==' } 3 { $carga += '=' } }
    $json = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($carga)) | ConvertFrom-Json
    return $json.$Claim
}

function Seccion([string]$Titulo) {
    Write-Host ''
    Write-Host "== $Titulo" -ForegroundColor Cyan
}

<# Registra una verificación. La condición debe devolver $true; si lanza excepción cuenta como falla. #>
function Probar([string]$Nombre, [scriptblock]$Condicion) {
    $detalle = ''
    try { $esCorrecto = [bool](& $Condicion) } catch { $esCorrecto = $false; $detalle = " → $($_.Exception.Message)" }
    if ($esCorrecto) {
        $script:Aprobadas++
        Write-Host "  [OK]    $Nombre" -ForegroundColor Green
    } else {
        $script:Fallidas++
        Write-Host "  [FALLA] $Nombre$detalle" -ForegroundColor Red
    }
}

function Mostrar-Resumen {
    Write-Host ''
    $color = if ($script:Fallidas -eq 0) { 'Green' } else { 'Red' }
    Write-Host "Resultado: $script:Aprobadas aprobadas, $script:Fallidas fallidas" -ForegroundColor $color
    exit ([int]($script:Fallidas -gt 0))
}
