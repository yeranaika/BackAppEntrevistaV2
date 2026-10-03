<#
.SYNOPSIS
    Prueba de punta a punta de la Fase 6 (práctica y nivelación): servidor real + Postgres real.

.DESCRIPTION
    Arma su propio banco (cargo, skill vinculada al cargo y 13 preguntas aprobadas por la API de administración)
    y recorre lo que usa la app Android (/api/prueba-practica con PR, BL y NV, e historial de intentos), la API
    /api/v1 (práctica con feedback inmediato, nivelación con brechas, niveles por skill), los tests de nivelación
    del admin, la sincronización offline idempotente y la evaluación freemium. Requiere la migración 017.
    No llama al LLM ni a APIs externas. Borra todo lo que crea.

.EXAMPLE
    pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_6_PRUEBA.ps1 -UrlBase http://127.0.0.1:8093
#>
param(
    [string]$UrlBase = 'http://127.0.0.1:8080',
    [string]$ContenedorBd = 'Entrevista_APP'
)

. "$PSScriptRoot/UTILIDAD_E2E.ps1"
Inicializar-E2E -UrlBase $UrlBase -ContenedorBd $ContenedorBd

$CONTRASENA = 'Clave-segura-1'
$sufijo = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$correoAdmin = "e2e_fase6_admin_$sufijo@prueba.local"
$correoAna = "e2e_fase6_ana_$sufijo@prueba.local"
$correoBeto = "e2e_fase6_beto_$sufijo@prueba.local"
$nombreCargo = "E2E Cargo Fase6 $sufijo"
$nombreSkill = "E2E Skill Fase6 $sufijo"
$tituloTest = "E2E Test Fase6 $sufijo"
$cargoId = [guid]::NewGuid().ToString()
$skillId = [guid]::NewGuid().ToString()
$TEXTO_CORRECTA = 'Opción correcta'
$RESPUESTA_IDEAL = 'Una corrutina se suspende sin bloquear el hilo y se reanuda después.'
$NIVELES_APP = @{ junior = 'jr'; semisenior = 'mid'; senior = 'sr' }

function Login([string]$Correo) { (Llamar-Api POST '/auth/login' @{ email = $Correo; password = $CONTRASENA }).Json.accessToken }
function IdDe([string]$Correo) { Ejecutar-Sql "select usuario_id from app.usuario where correo = '$Correo'" }
function PedidoApp([string]$Tipo) {
    @{ usuarioId = $null; nombreUsuario = 'Ana'; sector = 'mobile'; nivel = 'jr'; metaCargo = $nombreCargo; tipoPrueba = $Tipo; tipoPruebaEtiqueta = 'x'; tipo_prueba_etiqueta = 'x' }
}

function Crear-Pregunta([string]$Tipo, [string]$Categoria, [string]$Nivel, [int]$Numero) {
    $pregunta = @{ categoria = $Categoria; nivel = $Nivel; tipo = $Tipo; enunciado = "E2E Fase6 $Categoria $Nivel $Numero ($sufijo)"; respuestaIdeal = $RESPUESTA_IDEAL }
    if ($Categoria -eq 'tecnica') { $pregunta.skillId = $skillId } else { $pregunta.cargoId = $cargoId }
    if ($Tipo -eq 'opcion_multiple') {
        $pregunta.opciones = @(@{ texto = $TEXTO_CORRECTA; esCorrecta = $true; explicacion = 'Así funciona en Kotlin' }, @{ texto = 'Opción incorrecta' })
    }
    Llamar-Api POST '/api/v1/admin/preguntas' $pregunta -Token $admin
}

<# Respuestas como las arma la app: la correcta en las de nivel indicado, mal en el resto. #>
function Respuestas-App($Preguntas, [string[]]$NivelesQueAcierta) {
    @($Preguntas | ForEach-Object {
        $acierta = $_.nivel -in $NivelesQueAcierta
        if ($_.tipoPregunta -eq 'abierta') {
            @{ preguntaId = $_.preguntaId; opcionesSeleccionadas = @(); respuestaTexto = $(if ($acierta) { $RESPUESTA_IDEAL } else { 'no sé' }) }
        } else {
            $opcion = $_.configRespuesta.opciones | Where-Object { ($_.texto -eq $TEXTO_CORRECTA) -eq $acierta } | Select-Object -First 1
            @{ preguntaId = $_.preguntaId; opcionesSeleccionadas = @($opcion.id); respuestaTexto = $null }
        }
    })
}

Write-Host "Prueba E2E Fase 6 (práctica y nivelación) contra $UrlBase"

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
    # El cargo pide la skill en nivel senior (obligatoria) para ver la brecha.
    Ejecutar-Sql "insert into app.cargo_skill (cargo_id, skill_id, nivel_requerido, peso, obligatoria) values ('$cargoId', '$skillId', 'senior', 60, true)" | Out-Null
    $creadas = foreach ($nivel in @('junior', 'semisenior', 'senior')) {
        Crear-Pregunta 'opcion_multiple' 'tecnica' $nivel 1
        Crear-Pregunta 'opcion_multiple' 'tecnica' $nivel 2
        Crear-Pregunta 'abierta_texto' 'tecnica' $nivel 3
    }
    $creadas = @($creadas) + @(1..4 | ForEach-Object { Crear-Pregunta 'abierta_texto' 'blanda' 'junior' $_ })
    Probar 'cargo, skill vinculada y 13 preguntas aprobadas' { @($creadas | Where-Object { $_.Estado -eq 201 -and $_.Json.estado -eq 'aprobada' }).Count -eq 13 }

    Seccion 'App Android: práctica técnica (PR)'
    $pr = Llamar-Api POST '/api/prueba-practica/front' (PedidoApp 'PR') -Token $ana
    Probar 'POST /front PR → 201 con las 3 técnicas junior del cargo' { $pr.Estado -eq 201 -and $pr.Json.tipoPrueba -eq 'PR' -and @($pr.Json.preguntas).Count -eq 3 }
    Probar 'solo opcion_multiple y abierta, banco PR, sin revelar la correcta' {
        @($pr.Json.preguntas | Where-Object { $_.tipoPregunta -notin @('opcion_multiple', 'abierta') -or $_.tipoBanco -ne 'PR' }).Count -eq 0 -and $pr.Texto -notmatch 'es_correcta|respuesta_correcta'
    }
    $resultadoPr = Llamar-Api POST "/api/prueba-practica/$($pr.Json.pruebaId)/respuestas" @{ pruebaId = $pr.Json.pruebaId; respuestas = (Respuestas-App $pr.Json.preguntas @('jr')) } -Token $ana
    Probar 'POST /respuestas → 3/3 correctas, revisión NLP (freemium)' {
        $resultadoPr.Estado -eq 200 -and $resultadoPr.Json.puntaje -eq 3 -and $resultadoPr.Json.totalPreguntas -eq 3 -and $resultadoPr.Json.feedbackMode -eq 'nlp'
    }
    Probar 'en BD: práctica finalizada (100) con 3 respuestas puntuadas 0-10' {
        (Ejecutar-Sql "select s.estado || '|' || s.puntaje_sesion || '|' || count(r.*) || '|' || max(r.puntaje) from app.sesion_practica s join app.respuesta_practica r using (sesion_practica_id) where s.sesion_practica_id = '$($pr.Json.pruebaId)' group by s.estado, s.puntaje_sesion") -eq 'finalizada|100.00|3|10.00'
    }
    Probar 'en BD: el puntaje de la skill se acumuló sin fijar nivel' {
        (Ejecutar-Sql "select puntaje || '|' || num_evaluaciones || '|' || coalesce(nivel_evaluado, 'sin_nivel') from app.nivel_skill_usuario where skill_id = '$skillId' and usuario_id = '$(IdDe $correoAna)'") -eq '100.00|1|sin_nivel'
    }
    Probar 'responder otra vez → 409 practica_no_activa' {
        (Llamar-Api POST "/api/prueba-practica/$($pr.Json.pruebaId)/respuestas" @{ respuestas = (Respuestas-App $pr.Json.preguntas @('jr')) } -Token $ana).Json.error -eq 'practica_no_activa'
    }

    Seccion 'App Android: práctica blanda (BL)'
    $bl = Llamar-Api POST '/api/prueba-practica/front' (PedidoApp 'BL') -Token $ana
    Probar 'POST /front BL → 4 preguntas blandas abiertas con formato STAR' {
        @($bl.Json.preguntas).Count -eq 4 -and @($bl.Json.preguntas | Where-Object { $_.tipoBanco -eq 'BL' -and $_.configRespuesta.formato -eq 'STAR' }).Count -eq 4
    }

    Seccion 'App Android: nivelación (NV)'
    $nv = Llamar-Api POST '/api/prueba-practica/front' (PedidoApp 'NV') -Token $ana
    Probar 'POST /front NV → 9 preguntas, 3 por nivel (jr, mid, sr)' {
        $nv.Estado -eq 201 -and @($nv.Json.preguntas).Count -eq 9 -and (($nv.Json.preguntas.nivel | Select-Object -Unique) -join ',') -eq 'jr,mid,sr'
    }
    Probar 'la práctica BL sigue en curso (solo otra práctica la abandona)' { (Ejecutar-Sql "select estado from app.sesion_practica where sesion_practica_id = '$($bl.Json.pruebaId)'") -eq 'en_progreso' }
    $resultadoNv = Llamar-Api POST "/api/prueba-practica/$($nv.Json.pruebaId)/respuestas" @{ pruebaId = $nv.Json.pruebaId; respuestas = (Respuestas-App $nv.Json.preguntas @('jr', 'mid')) } -Token $ana
    Probar 'acierta junior y semisenior → nivel sugerido "Semi Senior", 6/9' {
        $resultadoNv.Estado -eq 200 -and $resultadoNv.Json.nivelDetectado -eq 'Semi Senior' -and $resultadoNv.Json.puntaje -eq 6 -and $resultadoNv.Json.totalPreguntas -eq 9
    }
    Probar 'el feedback nombra la skill a reforzar' { $resultadoNv.Json.feedbackGeneral -match [regex]::Escape($nombreSkill) }
    Probar 'en BD: intento cerrado con nivel semisenior y resultado con brecha alta' {
        (Ejecutar-Sql "select i.nivel_asignado || '|' || (i.fecha_fin is not null) || '|' || r.nivel_global_asignado || '|' || (r.skills_gap->0->>'prioridad') || '|' || (r.skills_gap->0->>'brecha') || '|' || (r.cargo_id = '$cargoId') from app.intento_test i join app.resultado_nivelacion r using (intento_id) where i.intento_id = '$($nv.Json.pruebaId)'") -eq 'semisenior|true|semisenior|alta|1|true'
    }
    Probar 'en BD: la skill quedó evaluada como semisenior' {
        (Ejecutar-Sql "select nivel_evaluado || '|' || num_evaluaciones from app.nivel_skill_usuario where skill_id = '$skillId' and usuario_id = '$(IdDe $correoAna)'") -eq 'semisenior|2'
    }
    Probar 'responder otra vez → 409 nivelacion_finalizada' {
        (Llamar-Api POST "/api/prueba-practica/$($nv.Json.pruebaId)/respuestas" @{ respuestas = (Respuestas-App $nv.Json.preguntas @('jr')) } -Token $ana).Json.error -eq 'nivelacion_finalizada'
    }

    Seccion 'Nivelación: envíos simultáneos'
    $nvBeto = (Llamar-Api POST '/api/prueba-practica/front' (PedidoApp 'NV') -Token $beto).Json
    $cuerpo = @{ respuestas = (Respuestas-App $nvBeto.preguntas @('jr')) }
    $simultaneos = Llamar-ApiEnParalelo @(1..4 | ForEach-Object { Nueva-Solicitud POST "/api/prueba-practica/$($nvBeto.pruebaId)/respuestas" $cuerpo $beto })
    Probar '4 envíos a la vez → exactamente 1 resultado (200) y 3 × 409' {
        @($simultaneos | Where-Object Estado -eq 200).Count -eq 1 -and @($simultaneos | Where-Object Estado -eq 409).Count -eq 3
    }
    Probar 'en BD: un solo resultado y la skill acumulada una sola vez' {
        (Ejecutar-Sql "select (select count(*) from app.resultado_nivelacion where intento_id = '$($nvBeto.pruebaId)') || '|' || (select num_evaluaciones from app.nivel_skill_usuario where skill_id = '$skillId' and usuario_id = '$(IdDe $correoBeto)')") -eq '1|1'
    }

    Seccion 'Historial de la app'
    $intentos = (Llamar-Api GET '/api/prueba-practica/intentos' -Token $ana).Json
    Probar 'GET /intentos → las 2 prácticas y la nivelación' { @($intentos).Count -eq 3 -and (($intentos.tipoPrueba | Sort-Object -Unique) -join ',') -eq 'nivelacion,practica' }
    Probar 'la práctica sin responder figura en curso y sin puntaje' { $b = $intentos | Where-Object pruebaId -eq $bl.Json.pruebaId; $b.estado -eq 'en_progreso' -and $null -eq $b.puntaje }
    Probar 'la nivelación figura con 6/9, nivel mid y finalizada' {
        $n = $intentos | Where-Object tipoPrueba -eq 'nivelacion'
        $n.puntaje -eq 6 -and $n.puntajeTotal -eq 9 -and $n.nivel -eq 'mid' -and $n.estado -eq 'finalizada' -and $n.intentoId -eq $n.pruebaId
    }

    Seccion 'API v1: práctica con feedback inmediato'
    $practica = Llamar-Api POST '/api/v1/practicas' @{ skillId = $skillId; modo = 'opcion_multiple'; nivel = 'senior'; cantidadPreguntas = 2 } -Token $beto
    Probar 'POST /api/v1/practicas → 201 con 2 alternativas senior' { $practica.Estado -eq 201 -and @($practica.Json.preguntas | Where-Object { $_.tipo -eq 'opcion_multiple' -and $_.nivel -eq 'senior' }).Count -eq 2 }
    $p1 = $practica.Json.preguntas[0]
    $mala = ($p1.opciones | Where-Object texto -ne $TEXTO_CORRECTA).opcionId
    $correccion = Llamar-Api POST "/api/v1/practicas/$($practica.Json.sesionId)/respuestas" @{ preguntaId = $p1.preguntaId; opcionId = $mala; tiempoRespuestaMs = 2500 } -Token $beto
    Probar 'responder → incorrecta, con la correcta y su explicación' {
        $correccion.Json.correcta -eq $false -and $correccion.Json.opcionCorrectaId -and $correccion.Json.feedback -eq 'Así funciona en Kotlin'
    }
    Probar 'en BD: tiempo de respuesta guardado' { (Ejecutar-Sql "select tiempo_respuesta_ms from app.respuesta_practica where sesion_practica_id = '$($practica.Json.sesionId)'") -eq '2500' }
    Probar 'modo inválido → 400 modo_invalido' { (Llamar-Api POST '/api/v1/practicas' @{ skillId = $skillId; modo = 'video' } -Token $beto).Json.error -eq 'modo_invalido' }
    Probar 'la práctica de otro usuario → 404' { (Llamar-Api GET "/api/v1/practicas/$($practica.Json.sesionId)" -Token $ana).Estado -eq 404 }
    Probar 'finalizar → finalizada con puntaje 0' { $f = (Llamar-Api POST "/api/v1/practicas/$($practica.Json.sesionId)/finalizar" -Token $beto).Json; $f.estado -eq 'finalizada' -and $f.puntaje -eq 0 }

    Seccion 'API v1: nivelación y niveles por skill'
    $ultimo = Llamar-Api GET '/api/v1/nivelacion/resultado' -Token $ana
    Probar 'GET /nivelacion/resultado → el último, con la brecha de la skill' { $ultimo.Json.intentoId -eq $nv.Json.pruebaId -and $ultimo.Json.brechas[0].nombre -eq $nombreSkill }
    Probar 'GET /me/niveles-skill → la skill con nivel semisenior' {
        $n = (Llamar-Api GET '/api/v1/me/niveles-skill' -Token $ana).Json | Where-Object skillId -eq $skillId
        $n.nivel -eq 'semisenior' -and $n.nombre -eq $nombreSkill
    }

    Seccion 'Tests de nivelación del admin'
    $idsTest = @($creadas | Where-Object { $_.Json.tipo -eq 'opcion_multiple' } | Select-Object -First 4 | ForEach-Object { $_.Json.id })
    $pedidoTest = @{ titulo = $tituloTest; cargoId = $cargoId; area = 'mobile'; preguntasIds = $idsTest }
    Probar 'usuario normal → 403' { (Llamar-Api POST '/api/v1/admin/tests-nivelacion' $pedidoTest -Token $ana).Estado -eq 403 }
    $test = Llamar-Api POST '/api/v1/admin/tests-nivelacion' $pedidoTest -Token $admin
    Probar 'admin crea el test (mixto) → 201' { $test.Estado -eq 201 -and $test.Json.nivelObjetivo -eq 'mixto' -and $test.Json.activo -eq $true }
    Probar 'pregunta inexistente → 404 pregunta_no_encontrada' {
        (Llamar-Api POST '/api/v1/admin/tests-nivelacion' (@{ titulo = 'x'; area = 'x'; preguntasIds = @($idsTest[0], $idsTest[1], [guid]::NewGuid().ToString()) }) -Token $admin).Json.error -eq 'pregunta_no_encontrada'
    }
    $nvConTest = (Llamar-Api POST '/api/v1/nivelacion' @{ cargoId = $cargoId } -Token $ana).Json
    Probar 'la nivelación usa el test del admin para ese cargo' {
        @($nvConTest.preguntas).Count -eq 4 -and (Ejecutar-Sql "select test_id from app.intento_test where intento_id = '$($nvConTest.intentoId)'") -eq $test.Json.testId
    }
    Probar 'DELETE → queda inactivo' {
        (Llamar-Api DELETE "/api/v1/admin/tests-nivelacion/$($test.Json.testId)" -Token $admin).Estado -eq 200 -and (Ejecutar-Sql "select activo from app.test_nivelacion where test_id = '$($test.Json.testId)'") -eq 'f'
    }

    Seccion 'Sincronización offline y evaluación freemium'
    $lote = @{ attempts = @(@{ localAttemptId = "e2e-local-$sufijo"; skillId = $skillId; modo = 'abierta_texto'; categoria = 'tecnica'; nivelPreguntas = 'junior';
        puntajeTotal = 8; fechaCreacionIso = '2026-01-15T10:00:00Z'; respuestas = @(@{ preguntaId = 'pregunta-offline-1'; enunciado = '¿Qué es una corrutina?'; respuestaTexto = 'Algo liviano'; esCorrecta = $true; puntaje = 8; orden = 1 }) }) }
    Probar 'sin token → 401 (antes era público y no guardaba nada)' { (Llamar-Api POST '/api/v1/sync/attempts' $lote).Estado -eq 401 }
    $sync1 = Llamar-Api POST '/api/v1/sync/attempts' $lote -Token $beto
    $sync2 = Llamar-Api POST '/api/v1/sync/attempts' $lote -Token $beto
    Probar 'sincroniza y el reintento devuelve el mismo id' { $sync1.Json.success -and $sync1.Json.mappings[0].serverAttemptId -eq $sync2.Json.mappings[0].serverAttemptId }
    Probar 'en BD: una sola práctica finalizada con su id local y fecha de la app' {
        (Ejecutar-Sql "select count(*) || '|' || max(estado) || '|' || max(fecha_inicio)::date from app.sesion_practica where id_local = 'e2e-local-$sufijo'") -eq '1|finalizada|2026-01-15'
    }
    Probar 'skill inexistente → 400 skill_no_encontrada' {
        $malo = @{ attempts = @(@{ localAttemptId = 'x'; skillId = [guid]::NewGuid().ToString(); modo = 'abierta_texto'; respuestas = @(@{ preguntaId = 'p'; enunciado = 'e'; orden = 1 }) }) }
        (Llamar-Api POST '/api/v1/sync/attempts' $malo -Token $beto).Json.error -eq 'skill_no_encontrada'
    }
    $evaluacion = @{ userText = 'Un deadlock bloquea procesos'; idealText = 'Un deadlock bloquea procesos que esperan recursos'; expectedKeywords = @('deadlock', 'recursos') }
    Probar 'evaluate-freemium sin token → 401' { (Llamar-Api POST '/api/v1/practice/evaluate-freemium' $evaluacion).Estado -eq 401 }
    Probar 'evaluate-freemium → falta "recursos"' { ((Llamar-Api POST '/api/v1/practice/evaluate-freemium' $evaluacion -Token $beto).Json.missingKeywords -join ',') -eq 'recursos' }
}
finally {
    Seccion 'Limpieza'
    Ejecutar-Sql "delete from app.test_nivelacion where titulo like 'E2E Test Fase6 %'" | Out-Null
    $preguntas = Ejecutar-Sql "delete from app.pregunta where enunciado like 'E2E Fase6 %'"
    $usuarios = Ejecutar-Sql "delete from app.usuario where correo like 'e2e\_fase6\_%@prueba.local'"
    Ejecutar-Sql "delete from app.cargo_skill where cargo_id in (select cargo_id from app.cargo where nombre like 'E2E Cargo Fase6 %')" | Out-Null
    Ejecutar-Sql "delete from app.skill where nombre like 'E2E Skill Fase6 %'" | Out-Null
    Ejecutar-Sql "delete from app.cargo where nombre like 'E2E Cargo Fase6 %'" | Out-Null
    Write-Host "  preguntas: $preguntas · usuarios: $usuarios (con sus prácticas, nivelaciones y niveles) · cargo, skill y test de prueba eliminados"
}

Mostrar-Resumen
