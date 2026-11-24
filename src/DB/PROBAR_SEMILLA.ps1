# Comprueba que la semilla se puede correr varias veces sin duplicar preguntas ni opciones.
# Usa una BD desechable (semilla_prueba) que crea y borra; no toca DBentrevista.
#
# Uso (PostgreSQL local o portátil, con PGUSER y PGPASSWORD definidos):
#   pwsh src/DB/PROBAR_SEMILLA.ps1 -Bin C:\ruta\pgsql\bin [-Puerto 5432]
param(
    [string]$Bin = $(if ($env:E2E_PSQL) { Split-Path -Parent $env:E2E_PSQL } else { "C:\Users\asus\pgportable\pgsql\bin" }),
    [int]$Puerto = 5432,
    [string]$Bd = "semilla_prueba"
)
$ErrorActionPreference = "Stop"
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$esquema = Join-Path $aqui "BasedeDatos.EntrevistaApp.sql"
$semilla = Join-Path $aqui "BasedeDatos.EntrevistaApp.Seeds.sql"
$migracion028 = Join-Path (Split-Path -Parent (Split-Path -Parent $aqui)) "migrations\028_modelo_vision_semilla_sin_expresiones.sql"
$env:PGPORT = "$Puerto"
$env:PGCLIENTENCODING = "UTF8"
# Sin avisos NOTICE: en PowerShell 5.1 cualquier línea de psql en stderr con 2>&1 se vuelve un error y corta el script.
$env:PGOPTIONS = "-c client_min_messages=warning"

function Sql([string]$archivo) {
    # El resultado lo dice el código de salida de psql (ON_ERROR_STOP), no el texto que escriba en stderr.
    $ErrorActionPreference = "Continue"
    $salida = & "$Bin\psql.exe" -q -X -v ON_ERROR_STOP=1 -d $Bd -f $archivo 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Falló $archivo`n$($salida -join "`n")" }
}
function Valor([string]$consulta) {
    $r = & "$Bin\psql.exe" -X -t -A -v ON_ERROR_STOP=1 -d $Bd -c "SET search_path TO app; $consulta"
    if ($LASTEXITCODE -ne 0) { throw "Falló la consulta: $consulta" }
    $ultimo = $r | Where-Object { $_ -ne "" -and $_ -ne "SET" } | Select-Object -Last 1
    if ($null -eq $ultimo) { "" } else { "$ultimo".Trim() }
}
function Conteo { Valor "select (select count(*) from pregunta) || '/' || (select count(*) from opcion_pregunta)" }
$fallas = 0
function Esperar([string]$que, $obtenido, $esperado) {
    if ("$obtenido" -eq "$esperado") { "  OK   $que ($obtenido)" }
    else { "  FALLA ${que}: se esperaba '$esperado' y salió '$obtenido'"; $script:fallas++ }
}

& "$Bin\dropdb.exe" --if-exists --force $Bd
& "$Bin\createdb.exe" $Bd
try {
    Sql $esquema
    Sql $semilla
    $primera = Conteo
    Esperar "primera corrida (preguntas/opciones)" $primera "14/24"

    Sql $semilla
    Esperar "segunda corrida no duplica" (Conteo) $primera
    Esperar "sin preguntas repetidas" (Valor "select count(*) from (select 1 from pregunta group by enunciado, skill_id, cargo_id, nivel_dificultad having count(*) > 1) d") "0"
    Esperar "cada alternativa con 4 opciones y 1 correcta" (Valor "select count(*) from (select pregunta_id from opcion_pregunta group by pregunta_id having count(*) <> 4 or count(*) filter (where es_correcta) <> 1) d") "0"

    # Una pregunta rechazada no vuelve a aprobarse y unas opciones editadas no se completan. (Una pregunta BORRADA, o
    # con otro enunciado, skill, cargo o nivel, sí vuelve a crearse: no queda nada con qué reconocerla.)
    Valor "update pregunta set estado = 'rechazada' where enunciado like '%tipo de JOIN retorna%'; select 1" | Out-Null
    Valor "delete from opcion_pregunta where orden = 4 and pregunta_id = (select pregunta_id from pregunta where enunciado like '%diferencia entre val y var%'); select 1" | Out-Null
    Sql $semilla
    Esperar "tercera corrida sigue con 14 preguntas" (Valor "select count(*) from pregunta") "14"
    Esperar "la rechazada sigue rechazada" (Valor "select estado from pregunta where enunciado like '%tipo de JOIN retorna%'") "rechazada"
    Esperar "no completa las opciones que editó el admin" (Valor "select count(*) from opcion_pregunta o join pregunta p using (pregunta_id) where p.enunciado like '%diferencia entre val y var%'") "3"

    # Consentimiento v1.1 con el permiso aparte de cámara (D35) y modelo de visión de ejemplo sin expresiones (D37).
    Esperar "v1.1 vigente con sus tres alcances" (Valor "select version || ' ' || alcances_posibles::text from consentimiento_texto where vigente") 'v1.1 ["estadisticas_uso", "marketing", "analisis_conducta_camara"]'
    Esperar "el texto v1.1 explica el permiso de cámara" (Valor "select count(*) from consentimiento_texto where version = 'v1.1' and cuerpo like '%permiso aparte%'") "1"
    Esperar "modelo de ejemplo deprecado y sin expresiones" (Valor "select estado || ' ' || (labels_detectados ? 'expresion_dominante')::text from modelo_vision_ia") "deprecado false"

    # La 028 corrige una BD sembrada con la semilla vieja y se puede correr dos veces.
    # (JSON armado en SQL: PowerShell 5.1 quita las comillas dobles al pasarlas a psql.)
    Valor "update modelo_vision_ia set estado = 'activo', labels_detectados = jsonb_build_array('contacto_visual', 'expresion_dominante'), metricas_rendimiento = jsonb_build_object('precision_expresion', 0.78); select 1" | Out-Null
    Sql $migracion028
    Sql $migracion028
    Esperar "la 028 deja el modelo deprecado, sin expresiones ni métricas" (Valor "select estado || ' ' || (labels_detectados ? 'expresion_dominante')::text || ' ' || coalesce(metricas_rendimiento::text, 'null') from modelo_vision_ia") "deprecado false null"
}
finally {
    & "$Bin\dropdb.exe" --if-exists --force $Bd
}
if ($fallas -gt 0) { throw "$fallas comprobaciones fallaron" }
"Semilla re-ejecutable: todo OK"
