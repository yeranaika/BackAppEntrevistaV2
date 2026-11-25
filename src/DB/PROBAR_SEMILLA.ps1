# Comprueba que la semilla (y la de áreas TI, D44) se puede correr varias veces sin duplicar preguntas ni opciones.
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
$semillaAreasTi = Join-Path $aqui "BasedeDatos.EntrevistaApp.Seeds.AreasTI.sql"
$migracion028 = Join-Path (Split-Path -Parent (Split-Path -Parent $aqui)) "migrations\028_modelo_vision_semilla_sin_expresiones.sql"
$migracion029 = Join-Path (Split-Path -Parent (Split-Path -Parent $aqui)) "migrations\029_plazo_consentimiento_cuenta_eliminada.sql"
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
    Esperar "el texto v1.1 dice el plazo de 5 años" (Valor "select count(*) from consentimiento_texto where version = 'v1.1' and cuerpo like '%durante 5 a_os desde que eliminas la cuenta%'") "1"

    # La 029 (D38) completa el registro de una cuenta eliminada antes de ella: sin IP, cerrado y con fecha de borrado.
    # Una cuenta activa no se toca. Se puede correr dos veces.
    Valor "insert into usuario (correo, contrasena_hash) values ('activa@prueba.cl', 'x'); select 1" | Out-Null
    Valor "insert into consentimiento (usuario_id, version, ip_origen) values ((select usuario_id from usuario where correo = 'activa@prueba.cl'), 'v1.1', '10.0.0.2'); select 1" | Out-Null
    Valor "insert into consentimiento (usuario_id, identidad_ref, version, ip_origen) values (null, 'borrada@prueba.cl', 'v1.1', '10.0.0.1'); select 1" | Out-Null
    Sql $migracion029
    Sql $migracion029
    Esperar "la 029 deja la cuenta eliminada sin IP, cerrada y con fecha" (Valor "select coalesce(ip_origen, 'sin-ip') || ' ' || (fecha_revocado is not null)::text || ' ' || (cuenta_eliminada_en is not null)::text || ' ' || retencion_legal::text from consentimiento where identidad_ref = 'borrada@prueba.cl'") "sin-ip true true false"
    Esperar "la 029 no toca una cuenta activa" (Valor "select ip_origen || ' ' || (fecha_revocado is null)::text || ' ' || (cuenta_eliminada_en is null)::text from consentimiento where ip_origen = '10.0.0.2'") "10.0.0.2 true true"

    # Áreas TI (D44): va después de la semilla base y se puede correr dos veces sin duplicar.
    # Sin tildes en las consultas: PowerShell 5.1 no pasa UTF-8 a psql -c. Las 13 skills nuevas se reconocen por su área.
    $nuevas = "(s.tipo_area in ('qa','seguridad','diseno','soporte','cloud') or s.nombre like 'Power BI%' or s.nombre like 'Excel para%')"
    $conteoAreas = "select (select count(*) from skill s where $nuevas) || '/' || (select count(*) from cargo where area in ('qa','seguridad','diseno','soporte','cloud') or nombre in ('Analista de Datos Junior','Analista BI SemiSenior')) || '/' || (select count(*) from pregunta p join skill s using (skill_id) where $nuevas) || '/' || (select count(*) from opcion_pregunta o join pregunta p using (pregunta_id) join skill s using (skill_id) where $nuevas)"
    Sql $semillaAreasTi
    Esperar "áreas TI: skills/cargos/preguntas/opciones" (Valor $conteoAreas) "13/12/234/468"
    Esperar "áreas TI: relaciones cargo-skill" (Valor "select count(*) from cargo_skill cs join cargo c using (cargo_id) where c.area in ('qa','seguridad','diseno','soporte','cloud') or c.nombre in ('Analista de Datos Junior','Analista BI SemiSenior')") "69"
    Sql $semillaAreasTi
    Esperar "áreas TI: segunda corrida no duplica" (Valor $conteoAreas) "13/12/234/468"
    Esperar "áreas TI: 6 aprobadas por skill y nivel (3 de opción múltiple)" (Valor "select count(*) from (select s.nombre, p.nivel_dificultad from pregunta p join skill s using (skill_id) where $nuevas and p.estado = 'aprobada' and p.cargo_id is null group by 1, 2 having count(*) = 6 and count(*) filter (where p.tipo_pregunta = 'opcion_multiple') = 3) d") "39"
    Esperar "áreas TI: cada alternativa con 4 opciones y 1 correcta" (Valor "select count(*) from pregunta p join skill s using (skill_id) where $nuevas and p.tipo_pregunta = 'opcion_multiple' and (select count(*) from opcion_pregunta o where o.pregunta_id = p.pregunta_id) = 4 and (select count(*) from opcion_pregunta o where o.pregunta_id = p.pregunta_id and o.es_correcta) = 1") "117"
    Esperar "áreas TI: abiertas con pasos y rúbrica completa" (Valor "select count(*) from pregunta p join skill s using (skill_id) where $nuevas and p.tipo_pregunta = 'abierta_texto' and p.respuesta_ideal like '% 1) % 2) % 3) %' and p.rubrica_evaluacion->>'metodo' = 'TECNICA' and jsonb_array_length(p.rubrica_evaluacion->'palabras_clave') >= 3 and p.rubrica_evaluacion->'feedback_generico' ?& array['correcto','medio','malo']") "117"
}
finally {
    & "$Bin\dropdb.exe" --if-exists --force $Bd
}
if ($fallas -gt 0) { throw "$fallas comprobaciones fallaron" }
"Semilla re-ejecutable: todo OK"
