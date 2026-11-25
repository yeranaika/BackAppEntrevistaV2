# Aplica un archivo SQL (semilla re-ejecutable o migración idempotente) a la BD de producción de Railway, conectándose
# por Tailscale (D43), con un respaldo previo del banco de preguntas.
#
# Uso (Tailscale conectado a la tailnet del proyecto; psql 16 o superior):
#   powershell -ExecutionPolicy Bypass -File src/DB/CARGAR_SQL_EN_RAILWAY.ps1 -Archivo src/DB/BasedeDatos.EntrevistaApp.Seeds.AreasTI.sql
#
# Pide la contraseña (PGPASSWORD del servicio Postgres en Railway → Variables) sin mostrarla ni guardarla: vive solo
# en este proceso. Antes de aplicar respalda en CSV skill, cargo, cargo_skill, pregunta y opcion_pregunta (sin datos de
# personas) en -Respaldo, muestra los conteos y pide escribir SI. Acordar con el equipo antes de aplicar algo aquí.
param(
    [Parameter(Mandatory = $true)] [string]$Archivo,
    [string]$Bin = $(if ($env:E2E_PSQL) { Split-Path -Parent $env:E2E_PSQL } else { "C:\Users\asus\pgportable\pgsql\bin" }),
    [string]$Servidor = "postgres.entrevistaappback-production-railway.internal",
    [int]$Puerto = 5432,
    [string]$Bd = "railway",
    [string]$Usuario = "postgres",
    [string]$Respaldo = (Join-Path $HOME "respaldos_railway")
)
$ErrorActionPreference = "Stop"
# Sin esto Test-NetConnection deja en pantalla "Waiting for response" aunque ya terminó, y confunde.
$ProgressPreference = "SilentlyContinue"
$psql = Join-Path $Bin "psql.exe"
if (-not (Test-Path $psql)) { throw "No encuentro psql en $Bin (usa -Bin o define E2E_PSQL)" }
$Archivo = (Resolve-Path $Archivo).Path

$conexion = Test-NetConnection -ComputerName $Servidor -Port $Puerto -WarningAction SilentlyContinue
if (-not $conexion.TcpTestSucceeded) {
    throw "No llego a ${Servidor}:$Puerto. ¿Tailscale está conectado y el servicio tailscale-rw de Railway en línea?"
}
"Conexión con Railway por Tailscale: OK"

$segura = Read-Host "Pega la contraseña de Postgres (PGPASSWORD) y presiona Enter; se ven asteriscos" -AsSecureString
$env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura))
if (-not $env:PGPASSWORD) { throw "No llegó ninguna contraseña: vuelve a ejecutar y pégala con clic derecho." }
"Recibí una contraseña de $($env:PGPASSWORD.Length) caracteres (la de Railway suele tener 32)."
$env:PGHOST = $Servidor
$env:PGPORT = "$Puerto"
$env:PGDATABASE = $Bd
$env:PGUSER = $Usuario
$env:PGCLIENTENCODING = "UTF8"
$env:PGOPTIONS = "-c client_min_messages=warning"

function Consulta([string]$sql) {
    $ErrorActionPreference = "Continue"
    $r = & $psql -X -t -A -v ON_ERROR_STOP=1 -c $sql 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Falló la consulta: $sql`n$($r -join "`n")" }
    $r
}
$conteos = "select 'skills=' || (select count(*) from app.skill) || ' cargos=' || (select count(*) from app.cargo) || ' relaciones=' || (select count(*) from app.cargo_skill) || ' preguntas=' || (select count(*) from app.pregunta) || ' aprobadas=' || (select count(*) from app.pregunta where estado = 'aprobada') || ' opciones=' || (select count(*) from app.opcion_pregunta)"

try {
    "Conectado a: " + (Consulta "select current_database() || ' en ' || split_part(version(), ',', 1)")

    $carpeta = Join-Path $Respaldo (Get-Date -Format "yyyy-MM-dd_HHmmss")
    New-Item -ItemType Directory -Force $carpeta | Out-Null
    foreach ($tabla in "skill", "cargo", "cargo_skill", "pregunta", "opcion_pregunta") {
        $destino = (Join-Path $carpeta "$tabla.csv").Replace("\", "/")
        Consulta "\copy app.$tabla to '$destino' with (format csv, header)" | Out-Null
    }
    "Respaldo del banco en: $carpeta"
    "Antes:   " + (Consulta $conteos)

    $respuesta = Read-Host "Aplicar $(Split-Path -Leaf $Archivo) a la BD de PRODUCCIÓN? Escribe SI para seguir"
    if ($respuesta -cne "SI") { "Cancelado: no se aplicó nada."; return }

    $ErrorActionPreference = "Continue"
    $salida = & $psql -X -q -v ON_ERROR_STOP=1 -f $Archivo 2>&1 | ForEach-Object { "$_" }
    $codigo = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($codigo -ne 0) { throw "Falló $Archivo (la transacción no dejó cambios si el archivo usa BEGIN/COMMIT):`n$($salida -join "`n")" }
    "Después: " + (Consulta $conteos)
    "Listo."
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
