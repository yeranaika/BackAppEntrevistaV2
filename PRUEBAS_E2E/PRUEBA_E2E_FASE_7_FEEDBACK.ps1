<#
.SYNOPSIS
    Prueba de punta a punta de la Fase 7 (reporte de feedback de la entrevista): servidor real + Postgres real.

.DESCRIPTION
    Arma su propio banco (cargo, skill vinculada y 8 preguntas aprobadas), rinde una entrevista con métricas de
    video y verifica el reporte que se genera en segundo plano: puntajes técnico, blando y de lenguaje corporal,
    radar por skill, fortalezas, áreas de mejora, recomendaciones, feedback por pregunta, el reintento manual
    tras un error, el historial de reportes y el progreso por skill.
    Por defecto el usuario no es premium: se evalúa con el motor freemium (sin costo).
    Con -ConIa el usuario canjea un código premium y se evalúa una entrevista con el LLM real (cuesta).
    Requiere la migración 018. Borra todo lo que crea.

.EXAMPLE
    pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_7_FEEDBACK.ps1 -UrlBase http://127.0.0.1:8093
    pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_7_FEEDBACK.ps1 -UrlBase http://127.0.0.1:8093 -ConIa
#>
param(
    [string]$UrlBase = 'http://127.0.0.1:8080',
    [string]$ContenedorBd = 'Entrevista_APP',
    [switch]$ConIa
)

. "$PSScriptRoot/UTILIDAD_E2E.ps1"
Inicializar-E2E -UrlBase $UrlBase -ContenedorBd $ContenedorBd

$CONTRASENA = 'Clave-segura-1'
$sufijo = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$correoAdmin = "e2e_fase7_admin_$sufijo@prueba.local"
$correoAna = "e2e_fase7_ana_$sufijo@prueba.local"
$correoBeto = "e2e_fase7_beto_$sufijo@prueba.local"
$nombreCargo = "E2E Cargo Fase7 $sufijo"
$nombreSkill = "E2E Skill Fase7 $sufijo"
$ETIQUETA_CODIGOS = 'e2e_fase7'
$cargoId = [guid]::NewGuid().ToString()
$skillId = [guid]::NewGuid().ToString()
$BASE = '/api/v1/entrevistas'
$TEXTO_CORRECTA = 'Opción correcta'
$RESPUESTA_IDEAL = 'Una corrutina se suspende sin bloquear el hilo y se reanuda después dentro de un scope.'

function Login([string]$Correo) { (Llamar-Api POST '/auth/login' @{ email = $Correo; password = $CONTRASENA }).Json.accessToken }

function Crear-Pregunta([string]$Tipo, [string]$Categoria, [int]$Numero) {
    $pregunta = @{ categoria = $Categoria; nivel = 'junior'; tipo = $Tipo; enunciado = "E2E Fase7 $Categoria $Numero ($sufijo)"; respuestaIdeal = $RESPUESTA_IDEAL }
    if ($Categoria -eq 'tecnica') { $pregunta.skillId = $skillId } else { $pregunta.cargoId = $cargoId }
    if ($Tipo -eq 'opcion_multiple') { $pregunta.opciones = @(@{ texto = $TEXTO_CORRECTA; esCorrecta = $true }, @{ texto = 'Opción incorrecta' }) }
    if ($Tipo -eq 'abierta_texto' -and $Categoria -eq 'tecnica') { $pregunta.rubrica = @{ criterios = @('explica'); palabras_clave = @('corrutina', 'hilo') } }
    Llamar-Api POST '/api/v1/admin/preguntas' $pregunta -Token $admin
}

<# Rinde una entrevista: alternativas correctas, técnicas abiertas con la respuesta ideal y "no sé" en las blandas. #>
function Rendir-Entrevista([string]$Token, [switch]$ConMetricas) {
    $sesion = (Llamar-Api POST $BASE @{ cargoId = $cargoId; nivel = 'jr' } -Token $Token).Json
    if ($ConMetricas) {
        $lote = @(0..29 | ForEach-Object { @{ timestampMs = $_ * 500; contactoVisual = 80; postura = 70; confianza = 60; expresion = 'seguro' } })
        Llamar-Api POST "$BASE/$($sesion.sesionId)/metricas" @{ metricas = $lote } -Token $Token | Out-Null
    }
    foreach ($p in $sesion.preguntas) {
        $cuerpo = if ($p.tipo -eq 'opcion_multiple') { @{ preguntaSesionId = $p.preguntaSesionId; opcionId = ($p.opciones | Where-Object texto -eq $TEXTO_CORRECTA).opcionId } }
                  elseif ($p.categoria -eq 'tecnica') { @{ preguntaSesionId = $p.preguntaSesionId; texto = $RESPUESTA_IDEAL } }
                  else { @{ preguntaSesionId = $p.preguntaSesionId; texto = 'no sé' } }
        Llamar-Api POST "$BASE/$($sesion.sesionId)/respuestas" $cuerpo -Token $Token | Out-Null
    }
    Llamar-Api POST "$BASE/$($sesion.sesionId)/finalizar" -Token $Token | Out-Null
    return $sesion.sesionId
}

<# Espera a que el reporte deje de estar "generando" (se genera en segundo plano). #>
function Esperar-Reporte([string]$SesionId, [string]$Token, [int]$Segundos = 60) {
    $limite = (Get-Date).AddSeconds($Segundos)
    do {
        $reporte = Llamar-Api GET "$BASE/$SesionId/reporte" -Token $Token
        if ($reporte.Json.estado -ne 'generando') { return $reporte }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $limite)
    return $reporte
}

Write-Host "Prueba E2E Fase 7 (feedback) contra $UrlBase"

try {
    Seccion 'Preparación (banco propio)'
    foreach ($correo in @($correoAdmin, $correoAna, $correoBeto)) {
        Llamar-Api POST '/auth/register' @{ email = $correo; password = $CONTRASENA } | Out-Null
    }
    Ejecutar-Sql "update app.usuario set rol = 'admin' where correo = '$correoAdmin'" | Out-Null
    $admin = Login $correoAdmin
    $ana = Login $correoAna
    $beto = Login $correoBeto
    Ejecutar-Sql "insert into app.cargo (cargo_id, nombre, area, nivel_base) values ('$cargoId', '$nombreCargo', 'mobile', 'junior')" | Out-Null
    Ejecutar-Sql "insert into app.skill (skill_id, nombre, categoria, tipo_area) values ('$skillId', '$nombreSkill', 'tecnica', 'mobile')" | Out-Null
    Ejecutar-Sql "insert into app.cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria) values ('$cargoId', '$skillId', 'junior', 60, true)" | Out-Null
    $creadas = @(1..3 | ForEach-Object { Crear-Pregunta 'opcion_multiple' 'tecnica' $_ }) +
        @(1..2 | ForEach-Object { Crear-Pregunta 'abierta_texto' 'tecnica' $_ }) +
        @(1..3 | ForEach-Object { Crear-Pregunta 'abierta_texto' 'blanda' $_ })
    Probar 'cargo, skill vinculada y 8 preguntas aprobadas' { @($creadas | Where-Object { $_.Estado -eq 201 }).Count -eq 8 }

    Seccion 'Reporte freemium generado en segundo plano'
    $sesionId = Rendir-Entrevista $ana -ConMetricas
    $inicial = Llamar-Api GET "$BASE/$sesionId/reporte" -Token $ana
    Probar 'apenas finalizada responde 200 (generando o ya listo)' { $inicial.Estado -eq 200 -and $inicial.Json.estado -in @('generando', 'listo') }
    $reporte = Esperar-Reporte $sesionId $ana
    $r = $reporte.Json
    Probar 'queda listo, evaluado con el motor freemium' { $r.estado -eq 'listo' -and $r.modoEvaluacion -eq 'freemium' }
    Probar 'técnico alto y blando bajo' { $r.puntajeTecnico -gt 85 -and $r.puntajeBlando -lt 30 }
    Probar 'lenguaje corporal = promedio de contacto, postura y confianza (70)' { $r.puntajeLenguajeCorporal -eq 70 }
    Probar 'global = 50 % técnico + 30 % blando + 20 % corporal' {
        [math]::Abs($r.puntajeGlobal - ($r.puntajeTecnico * 0.5 + $r.puntajeBlando * 0.3 + 70 * 0.2)) -lt 0.05
    }
    Probar 'radar: la skill y el grupo de habilidades blandas' { (($r.skills.nombre | Sort-Object) -join '|') -eq (@('Habilidades blandas', $nombreSkill) | Sort-Object) -join '|' }
    Probar 'fortalezas y áreas de mejora' { "Dominio de $nombreSkill" -in $r.fortalezas -and 'Reforzar Habilidades blandas' -in $r.areasMejora }
    Probar 'feedback por pregunta: las abiertas con puntaje y observación' {
        @($r.preguntas | Where-Object { $_.tipo -eq 'abierta_texto' -and $null -ne $_.puntaje -and $_.observacion }).Count -eq 5
    }
    Probar 'no expone datos internos (modelo, tokens, costo)' { $reporte.Texto -notmatch 'costo|tokens|modelo' }
    Probar 'en BD: reporte listo, freemium, 1 intento y sin costo' {
        (Ejecutar-Sql "select estado_generacion || '|' || modo_evaluacion || '|' || intentos_generacion || '|' || (costo_usd is null) from app.reporte_entrevista where sesion_id = '$sesionId'") -eq 'listo|freemium|1|true'
    }
    Probar 'en BD: 2 filas de detalle por skill y feedback en las 5 abiertas' {
        (Ejecutar-Sql "select (select count(*) from app.reporte_skill_detalle d join app.reporte_entrevista r using (reporte_id) where r.sesion_id = '$sesionId') || '|' || (select count(*) from app.sesion_pregunta_respuesta where sesion_id = '$sesionId' and coalesce(feedback_ia_tecnico, feedback_ia_blando) is not null)") -eq '2|5'
    }
    Probar 'en BD: el puntaje de la skill se acumuló' {
        (Ejecutar-Sql "select num_evaluaciones from app.nivel_skill_usuario where skill_id = '$skillId' and usuario_id = (select usuario_id from app.usuario where correo = '$correoAna')") -eq '1'
    }
    Probar 'reintentar un reporte listo → 409 reporte_no_reintentable' {
        (Llamar-Api POST "$BASE/$sesionId/reporte/reintentar" -Token $ana).Json.error -eq 'reporte_no_reintentable'
    }

    Seccion 'Reintento manual tras un error'
    Ejecutar-Sql "update app.reporte_entrevista set estado_generacion = 'error', error_detalle = 'error_interno' where sesion_id = '$sesionId'" | Out-Null
    $conError = (Llamar-Api GET "$BASE/$sesionId/reporte" -Token $ana).Json
    Probar 'en error: mensaje claro para el usuario, sin el código interno, y se puede reintentar' {
        $conError.estado -eq 'error' -and $conError.error -and $conError.error -notmatch 'error_interno' -and $conError.puedeReintentar -eq $true
    }
    $reintento = Llamar-Api POST "$BASE/$sesionId/reporte/reintentar" -Token $ana
    Probar 'POST /reintentar → 202' { $reintento.Estado -eq 202 }
    Probar 'vuelve a quedar listo, con 2 intentos' {
        (Esperar-Reporte $sesionId $ana).Json.estado -eq 'listo' -and (Ejecutar-Sql "select intentos_generacion from app.reporte_entrevista where sesion_id = '$sesionId'") -eq '2'
    }

    Seccion 'Permisos y estados'
    Probar 'sin token → 401' { (Llamar-Api GET "$BASE/$sesionId/reporte").Estado -eq 401 }
    Probar 'el reporte de otro usuario → 404' { (Llamar-Api GET "$BASE/$sesionId/reporte" -Token $beto).Estado -eq 404 }
    $enCurso = (Llamar-Api POST $BASE @{ cargoId = $cargoId; nivel = 'jr'; cantidadPreguntas = 3 } -Token $beto).Json.sesionId
    Probar 'entrevista en curso → 409 entrevista_no_finalizada' { (Llamar-Api GET "$BASE/$enCurso/reporte" -Token $beto).Json.error -eq 'entrevista_no_finalizada' }
    Llamar-Api POST "$BASE/$enCurso/cancelar" -Token $beto | Out-Null
    Probar 'entrevista cancelada → 404 reporte_no_disponible' { (Llamar-Api GET "$BASE/$enCurso/reporte" -Token $beto).Json.error -eq 'reporte_no_disponible' }

    Seccion 'Historial y progreso'
    $reportes = (Llamar-Api GET '/api/v1/reportes' -Token $ana).Json
    Probar 'GET /api/v1/reportes → la entrevista con su puntaje' { @($reportes).Count -eq 1 -and $reportes[0].sesionId -eq $sesionId -and $reportes[0].puntajeGlobal -eq $r.puntajeGlobal }
    $progreso = (Llamar-Api GET '/api/v1/me/progreso' -Token $ana).Json | Where-Object skillId -eq $skillId
    Probar 'GET /api/v1/me/progreso → la skill con su historial' { $progreso.nombre -eq $nombreSkill -and @($progreso.historial).Count -eq 1 }

    if ($ConIa) {
        Seccion 'Evaluación con IA para premium (-ConIa)'
        $codigo = (Llamar-Api POST '/billing/admin/codes' @{ days = 1; label = $ETIQUETA_CODIGOS; max_uses = 1; license_type = 'PROM' } -Token $admin).Json.code
        Llamar-Api POST '/billing/code/redeem' @{ code = $codigo } -Token $ana | Out-Null
        $sesionIa = Rendir-Entrevista $ana
        $ia = (Esperar-Reporte $sesionIa $ana 120).Json
        Probar "reporte con IA listo (modo: $($ia.modoEvaluacion))" { $ia.estado -eq 'listo' -and $ia.modoEvaluacion -eq 'ia' -and $ia.resumen }
        Probar 'en BD: modelo, tokens y costo registrados' {
            (Ejecutar-Sql "select (modelo_llm is not null) || '|' || (tokens_entrada > 0) || '|' || (costo_usd > 0) from app.reporte_entrevista where sesion_id = '$sesionIa'") -eq 'true|true|true'
        }
    }
}
finally {
    Seccion 'Limpieza'
    $condicion = "usuario_id in (select usuario_id from app.usuario where correo like 'e2e\_fase7\_%@prueba.local')"
    Ejecutar-Sql "delete from app.suscripcion where $condicion" | Out-Null
    Ejecutar-Sql "delete from app.codigo_suscripcion where label = '$ETIQUETA_CODIGOS'" | Out-Null
    $preguntas = Ejecutar-Sql "delete from app.pregunta where enunciado like 'E2E Fase7 %'"
    $usuarios = Ejecutar-Sql "delete from app.usuario where correo like 'e2e\_fase7\_%@prueba.local'"
    Ejecutar-Sql "delete from app.cargo_skill where cargo_id in (select cargo_id from app.cargo where nombre like 'E2E Cargo Fase7 %')" | Out-Null
    Ejecutar-Sql "delete from app.skill where nombre like 'E2E Skill Fase7 %'" | Out-Null
    Ejecutar-Sql "delete from app.cargo where nombre like 'E2E Cargo Fase7 %'" | Out-Null
    Write-Host "  preguntas: $preguntas · usuarios: $usuarios (con sus entrevistas y reportes) · cargo y skill de prueba eliminados"
}

Mostrar-Resumen
