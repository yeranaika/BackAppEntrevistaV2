<#
.SYNOPSIS
    Prueba de punta a punta de la Fase 5 (simulación de entrevista): servidor real + Postgres real.

.DESCRIPTION
    Arma su propio banco (un cargo y 10 preguntas aprobadas creadas por la API de administración) y recorre:
    la API por sesión (iniciar, siguiente, responder, métricas de video en lote, finalizar, cancelar),
    las reglas (una sola entrevista en curso, también con solicitudes simultáneas; entrevistas ajenas = 404;
    no revelar la corrección durante la sesión; snapshot inmutable) y el contrato que usa la app Android
    (/api/prueba-practica/front y /respuestas). Requiere la migración 016.
    No llama al LLM ni a APIs externas. Borra todo lo que crea (usuarios e2e_fase5_*, cargo, preguntas y sesiones).

.EXAMPLE
    pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_5_ENTREVISTA.ps1 -UrlBase http://127.0.0.1:8093
#>
param(
    [string]$UrlBase = 'http://127.0.0.1:8080',
    [string]$ContenedorBd = 'Entrevista_APP'
)

. "$PSScriptRoot/UTILIDAD_E2E.ps1"
Inicializar-E2E -UrlBase $UrlBase -ContenedorBd $ContenedorBd

$CONTRASENA = 'Clave-segura-1'
$sufijo = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$correoAdmin = "e2e_fase5_admin_$sufijo@prueba.local"
$correoAna = "e2e_fase5_ana_$sufijo@prueba.local"
$correoBeto = "e2e_fase5_beto_$sufijo@prueba.local"
$nombreCargo = "E2E Cargo Fase5 $sufijo"
$BASE = '/api/v1/entrevistas'
$TEXTO_CORRECTA = 'Opción correcta'

function Login([string]$Correo) { (Llamar-Api POST '/auth/login' @{ email = $Correo; password = $CONTRASENA }).Json.accessToken }
function IdDe([string]$Correo) { Ejecutar-Sql "select usuario_id from app.usuario where correo = '$Correo'" }

function Crear-Pregunta([string]$Categoria, [int]$Numero) {
    $pregunta = @{ cargoId = $cargoId; categoria = $Categoria; nivel = 'junior'; enunciado = "E2E Fase5 $Categoria $Numero ($sufijo)"; respuestaIdeal = 'Respuesta ideal' }
    if ($Categoria -eq 'tecnica') {
        $pregunta.tipo = 'opcion_multiple'
        $pregunta.opciones = @(@{ texto = $TEXTO_CORRECTA; esCorrecta = $true }, @{ texto = 'Opción incorrecta A' }, @{ texto = 'Opción incorrecta B' })
    } else {
        $pregunta.tipo = 'abierta_texto'
    }
    Llamar-Api POST '/api/v1/admin/preguntas' $pregunta -Token $admin
}

<# Responde la pregunta: la opción correcta si es de alternativas, un texto si es abierta. #>
function Respuesta-Para($Pregunta) {
    if ($Pregunta.tipo -eq 'opcion_multiple') {
        return @{ preguntaSesionId = $Pregunta.preguntaSesionId; opcionId = ($Pregunta.opciones | Where-Object texto -eq $TEXTO_CORRECTA).opcionId }
    }
    return @{ preguntaSesionId = $Pregunta.preguntaSesionId; texto = 'Usé el método STAR: situación, tarea, acción y resultado.' }
}

Write-Host "Prueba E2E Fase 5 (entrevista) contra $UrlBase"

try {
    Seccion 'Preparación (banco propio por la API de administración)'
    foreach ($correo in @($correoAdmin, $correoAna, $correoBeto)) {
        Llamar-Api POST '/auth/register' @{ email = $correo; password = $CONTRASENA } | Out-Null
    }
    Ejecutar-Sql "update app.usuario set rol = 'admin' where correo = '$correoAdmin'" | Out-Null
    $admin = Login $correoAdmin
    $ana = Login $correoAna
    $beto = Login $correoBeto
    $cargoId = (Llamar-Api POST '/admin/market/cargos' @{ nombre = $nombreCargo; area = 'backend'; nivelBase = 'junior'; autoGenerateSkills = $false } -Token $admin).Json.cargo.cargoId
    $creadas = @(1..6 | ForEach-Object { Crear-Pregunta 'tecnica' $_ }) + @(1..4 | ForEach-Object { Crear-Pregunta 'blanda' $_ })
    Probar 'cargo y 10 preguntas aprobadas (6 técnicas, 4 blandas)' { $cargoId -and @($creadas | Where-Object { $_.Estado -eq 201 -and $_.Json.estado -eq 'aprobada' }).Count -eq 10 }

    Seccion 'Iniciar'
    Probar 'sin token → 401' { (Llamar-Api POST $BASE @{ cargoId = $cargoId }).Estado -eq 401 }
    Probar 'sin entrevista en curso → 204' { (Llamar-Api GET "$BASE/actual" -Token $ana).Estado -eq 204 }
    $creada = Llamar-Api POST $BASE @{ cargoId = $cargoId; nivel = 'jr' } -Token $ana
    $sesion = $creada.Json
    $sesionId = $sesion.sesionId
    Probar 'POST /api/v1/entrevistas → 201 en_progreso con 8 preguntas' { $creada.Estado -eq 201 -and $sesion.estado -eq 'en_progreso' -and $sesion.totalPreguntas -eq 8 }
    Probar '5 técnicas primero y 3 blandas al final' { ($sesion.preguntas.categoria -join ',') -eq 'tecnica,tecnica,tecnica,tecnica,tecnica,blanda,blanda,blanda' }
    Probar 'todas salen del banco del cargo' {
        (Ejecutar-Sql "select count(*) from app.sesion_pregunta_respuesta s join app.pregunta p using (pregunta_id) where s.sesion_id = '$sesionId' and p.cargo_id = '$cargoId'") -eq '8'
    }
    Probar 'no revela la respuesta correcta durante la sesión' { $creada.Texto -notmatch 'correccion|esCorrecta|es_correcta' }
    Probar 'snapshot con tipo, categoría y opciones en BD' {
        (Ejecutar-Sql "select count(*) from app.sesion_pregunta_respuesta where sesion_id = '$sesionId' and tipo_pregunta is not null and categoria_habilidad is not null and (tipo_pregunta <> 'opcion_multiple' or jsonb_array_length(opciones_snap) = 3)") -eq '8'
    }
    Probar 'cada pregunta usada suma un uso' { (Ejecutar-Sql "select count(*) from app.pregunta where cargo_id = '$cargoId' and veces_usada = 1") -eq '8' }
    Probar 'GET /actual → la misma entrevista' { (Llamar-Api GET "$BASE/actual" -Token $ana).Json.sesionId -eq $sesionId }
    $otra = Llamar-Api POST $BASE @{ cargoId = $cargoId } -Token $ana
    Probar 'una segunda entrevista en curso → 409 entrevista_en_progreso' { $otra.Estado -eq 409 -and $otra.Json.error -eq 'entrevista_en_progreso' }
    Probar 'nivel inválido → 400 nivel_invalido' { (Llamar-Api POST $BASE @{ cargoId = $cargoId; nivel = 'experto' } -Token $beto).Json.error -eq 'nivel_invalido' }
    Probar 'cargo inexistente → 404 cargo_no_encontrado' { (Llamar-Api POST $BASE @{ cargoId = [guid]::NewGuid().ToString() } -Token $beto).Json.error -eq 'cargo_no_encontrado' }
    Probar 'nivel sin preguntas → 409 preguntas_insuficientes' {
        (Llamar-Api POST $BASE @{ cargoId = $cargoId; nivel = 'senior'; cantidadPreguntas = 15 } -Token $beto).Json.error -eq 'preguntas_insuficientes'
    }

    Seccion 'Responder pregunta a pregunta'
    Probar 'la entrevista de otro usuario → 404' { (Llamar-Api GET "$BASE/$sesionId" -Token $beto).Estado -eq 404 }
    $primera = (Llamar-Api GET "$BASE/$sesionId/siguiente" -Token $ana).Json
    Probar 'siguiente → la pregunta 1' { $primera.orden -eq 1 }
    $incorrecta = ($primera.opciones | Where-Object texto -ne $TEXTO_CORRECTA)[0].opcionId
    Probar 'opción que no es de la pregunta → 400 opcion_invalida' {
        (Llamar-Api POST "$BASE/$sesionId/respuestas" @{ preguntaSesionId = $primera.preguntaSesionId; opcionId = [guid]::NewGuid().ToString() } -Token $ana).Json.error -eq 'opcion_invalida'
    }
    $respondida = Llamar-Api POST "$BASE/$sesionId/respuestas" @{ preguntaSesionId = $primera.preguntaSesionId; opcionId = $incorrecta } -Token $ana
    Probar 'responder → 200 respondida' { $respondida.Estado -eq 200 -and $respondida.Json.respondida -eq $true }
    Probar 'en BD: opción elegida, puntaje 0 y fecha de respuesta' {
        (Ejecutar-Sql "select (opcion_elegida_id = '$incorrecta') || '|' || puntaje_respuesta || '|' || (fecha_respuesta is not null) from app.sesion_pregunta_respuesta where respuesta_id = '$($primera.preguntaSesionId)'") -eq 'true|0.00|true'
    }
    Probar 'responder de nuevo → 409 pregunta_ya_respondida' {
        (Llamar-Api POST "$BASE/$sesionId/respuestas" @{ preguntaSesionId = $primera.preguntaSesionId; opcionId = $incorrecta } -Token $ana).Json.error -eq 'pregunta_ya_respondida'
    }
    $resto = 2..8 | ForEach-Object {
        $pregunta = (Llamar-Api GET "$BASE/$sesionId/siguiente" -Token $ana).Json
        (Llamar-Api POST "$BASE/$sesionId/respuestas" (Respuesta-Para $pregunta) -Token $ana).Estado
    }
    Probar 'responde las otras 7 (opción correcta y respuestas STAR)' { @($resto | Where-Object { $_ -eq 200 }).Count -eq 7 }
    Probar 'sin preguntas pendientes → 204' { (Llamar-Api GET "$BASE/$sesionId/siguiente" -Token $ana).Estado -eq 204 }

    Seccion 'Métricas de video en lote'
    $lote = @(0..29 | ForEach-Object { @{ timestampMs = $_ * 500; contactoVisual = 80.5; postura = 70; confianza = 65.25; expresion = 'seguro'; gestos = @{ toca_cara = ($_ % 2 -eq 0) } } })
    $metricas = Llamar-Api POST "$BASE/$sesionId/metricas" @{ metricas = $lote } -Token $ana
    Probar '30 métricas → 201 insertadas=30' { $metricas.Estado -eq 201 -and $metricas.Json.insertadas -eq 30 }
    Probar 'en BD 30 filas con sus valores' { (Ejecutar-Sql "select count(*) || '|' || max(contacto_visual) || '|' || count(gestos_detectados) from app.metrica_video where sesion_id = '$sesionId'") -eq '30|80.50|30' }
    Probar 'puntaje fuera de rango → 400 y no guarda nada' {
        $r = Llamar-Api POST "$BASE/$sesionId/metricas" @{ metricas = @(@{ timestampMs = 1; postura = 150 }) } -Token $ana
        $r.Json.error -eq 'metrica_fuera_de_rango' -and (Ejecutar-Sql "select count(*) from app.metrica_video where sesion_id = '$sesionId'") -eq '30'
    }
    Probar 'expresión inválida → 400 expresion_invalida' {
        (Llamar-Api POST "$BASE/$sesionId/metricas" @{ metricas = @(@{ timestampMs = 1; expresion = 'feliz' }) } -Token $ana).Json.error -eq 'expresion_invalida'
    }
    Probar 'lote de más de 300 → 400 lote_invalido' {
        (Llamar-Api POST "$BASE/$sesionId/metricas" @{ metricas = @(0..300 | ForEach-Object { @{ timestampMs = $_ } }) } -Token $ana).Json.error -eq 'lote_invalido'
    }

    Seccion 'Finalizar'
    $preguntaEditada = $sesion.preguntas[0]
    $idBanco = Ejecutar-Sql "select pregunta_id from app.sesion_pregunta_respuesta where respuesta_id = '$($preguntaEditada.preguntaSesionId)'"
    Ejecutar-Sql "update app.pregunta set enunciado = 'Enunciado editado después' where pregunta_id = '$idBanco'" | Out-Null
    $finalizada = Llamar-Api POST "$BASE/$sesionId/finalizar" -Token $ana
    Probar 'finalizar → finalizada con fecha de fin' { $finalizada.Json.estado -eq 'finalizada' -and $finalizada.Json.fechaFin }
    Probar 'ahora sí muestra la corrección (4 de 5 técnicas correctas)' {
        @($finalizada.Json.preguntas | Where-Object { $_.correccion.esCorrecta -eq $true }).Count -eq 4
    }
    Probar 'editar el banco no cambió la sesión (snapshot)' { $finalizada.Json.preguntas[0].enunciado -eq $preguntaEditada.enunciado }
    Probar 'responder una finalizada → 409 entrevista_no_activa' {
        (Llamar-Api POST "$BASE/$sesionId/respuestas" @{ preguntaSesionId = $primera.preguntaSesionId; texto = 'x' } -Token $ana).Json.error -eq 'entrevista_no_activa'
    }
    Probar 'métricas en una finalizada → 409' { (Llamar-Api POST "$BASE/$sesionId/metricas" @{ metricas = @(@{ timestampMs = 1 }) } -Token $ana).Estado -eq 409 }
    Probar 'finalizar otra vez → 409' { (Llamar-Api POST "$BASE/$sesionId/finalizar" -Token $ana).Estado -eq 409 }
    $historial = (Llamar-Api GET "${BASE}?pagina=1&tamano=10" -Token $ana).Json
    Probar 'historial → 1 entrevista con 8/8 respondidas' { $historial.total -eq 1 -and $historial.elementos[0].respondidas -eq 8 }

    Seccion 'Cancelar y validaciones'
    $nueva = (Llamar-Api POST $BASE @{ cargo = $nombreCargo.ToUpper(); nivel = 'junior'; cantidadPreguntas = 3 } -Token $ana).Json
    Probar 'por nombre del cargo (sin distinguir mayúsculas) → usa el cargo del catálogo' { $nueva.cargoId -eq $cargoId }
    Probar 'finalizar sin respuestas → 400 sin_respuestas' { (Llamar-Api POST "$BASE/$($nueva.sesionId)/finalizar" -Token $ana).Json.error -eq 'sin_respuestas' }
    $abierta = $nueva.preguntas | Where-Object tipo -eq 'abierta_texto' | Select-Object -First 1
    if ($abierta) {
        Probar 'abierta en blanco → 400 respuesta_requerida' {
            (Llamar-Api POST "$BASE/$($nueva.sesionId)/respuestas" @{ preguntaSesionId = $abierta.preguntaSesionId; texto = '   ' } -Token $ana).Json.error -eq 'respuesta_requerida'
        }
    }
    Probar 'el índice único de la BD impide una segunda sesión en curso' {
        $idAna = IdDe $correoAna
        try { Ejecutar-Sql "insert into app.sesion_entrevista (usuario_id, cargo_objetivo, nivel_dificultad) values ('$idAna', 'x', 'junior')" | Out-Null; $false }
        catch { $_.Exception.Message -match 'idx_sesion_activa_unica' }
    }
    Probar 'cancelar → cancelada' { (Llamar-Api POST "$BASE/$($nueva.sesionId)/cancelar" -Token $ana).Json.estado -eq 'cancelada' }

    Seccion 'Inicios simultáneos'
    $simultaneas = Llamar-ApiEnParalelo @(1..5 | ForEach-Object { Nueva-Solicitud POST $BASE @{ cargoId = $cargoId; nivel = 'junior'; cantidadPreguntas = 3 } $beto })
    Probar '5 inicios a la vez → exactamente 1 entrevista (201) y 4 × 409' {
        @($simultaneas | Where-Object Estado -eq 201).Count -eq 1 -and @($simultaneas | Where-Object Estado -eq 409).Count -eq 4
    }
    Probar 'en BD beto tiene una sola en progreso' {
        (Ejecutar-Sql "select count(*) from app.sesion_entrevista where usuario_id = '$(IdDe $correoBeto)' and estado = 'en_progreso'") -eq '1'
    }

    Seccion 'Contrato de la app Android (/api/prueba-practica)'
    $pedidoApp = @{ usuarioId = $null; nombreUsuario = 'Ana'; sector = 'backend'; nivel = 'jr'; metaCargo = $nombreCargo.ToLower(); tipoPrueba = 'ENT'; tipoPruebaEtiqueta = 'blended'; tipo_prueba_etiqueta = 'blended' }
    $prueba = Llamar-Api POST '/api/prueba-practica/front' $pedidoApp -Token $ana
    Probar 'POST /front (ENT) → 2xx con 8 preguntas' { $prueba.Estado -in 200..299 -and @($prueba.Json.preguntas).Count -eq 8 }
    Probar 'trae area, nivel y metadata (la app los exige)' { $prueba.Json.area -eq 'backend' -and $prueba.Json.nivel -eq 'jr' -and $prueba.Json.metadata.cargoId -eq $cargoId }
    Probar 'solo tipos que la app sabe dibujar (opcion_multiple / abierta)' { @($prueba.Json.preguntas | Where-Object { $_.tipoPregunta -notin @('opcion_multiple', 'abierta') }).Count -eq 0 }
    Probar 'configRespuesta con opciones {id, texto} y límites de caracteres' {
        $m = $prueba.Json.preguntas | Where-Object tipoPregunta -eq 'opcion_multiple' | Select-Object -First 1
        $a = $prueba.Json.preguntas | Where-Object tipoPregunta -eq 'abierta' | Select-Object -First 1
        @($m.configRespuesta.opciones).Count -eq 3 -and $m.configRespuesta.opciones[0].id -and $a.configRespuesta.max_caracteres -gt 0
    }
    $respuestasApp = @($prueba.Json.preguntas | ForEach-Object {
        if ($_.tipoPregunta -eq 'opcion_multiple') {
            @{ preguntaId = $_.preguntaId; opcionesSeleccionadas = @(($_.configRespuesta.opciones | Where-Object texto -eq $TEXTO_CORRECTA).id) }
        } else {
            @{ preguntaId = $_.preguntaId; respuestaAbierta = 'Situación, tarea, acción y resultado.' }
        }
    })
    $resultado = Llamar-Api POST "/api/prueba-practica/$($prueba.Json.pruebaId)/respuestas" @{ pruebaId = $prueba.Json.pruebaId; respuestas = $respuestasApp } -Token $ana
    Probar 'POST /respuestas → 200 ok' { $resultado.Estado -eq 200 -and $resultado.Json.ok -eq $true }
    Probar 'puntaje 5 / 5 (todas las alternativas correctas) y 8 respondidas' {
        $resultado.Json.puntaje -eq 5 -and $resultado.Json.totalPreguntas -eq 5 -and $resultado.Json.respondidas -eq 8 -and @($resultado.Json.detalle).Count -eq 5
    }
    Probar 'feedback explica que las abiertas van al reporte' { $resultado.Json.feedbackGeneral -match '3 respuestas abiertas' }
    Probar 'en BD la sesión quedó finalizada' { (Ejecutar-Sql "select estado from app.sesion_entrevista where sesion_id = '$($prueba.Json.pruebaId)'") -eq 'finalizada' }
    $pedidoPractica = $pedidoApp.Clone()
    $pedidoPractica.tipoPrueba = 'PR'
    Probar 'tipo PR (práctica, Fase 6) → 400 tipo_prueba_no_soportado' {
        (Llamar-Api POST '/api/prueba-practica/front' $pedidoPractica -Token $ana).Json.error -eq 'tipo_prueba_no_soportado'
    }
}
finally {
    Seccion 'Limpieza'
    $condicion = "usuario_id in (select usuario_id from app.usuario where correo like 'e2e\_fase5\_%@prueba.local')"
    $sesiones = Ejecutar-Sql "delete from app.sesion_entrevista where $condicion"
    $preguntas = Ejecutar-Sql "delete from app.pregunta where enunciado like 'E2E Fase5 %' or cargo_id in (select cargo_id from app.cargo where nombre like 'E2E Cargo Fase5 %')"
    Ejecutar-Sql "delete from app.cargo where nombre like 'E2E Cargo Fase5 %'" | Out-Null
    $usuarios = Ejecutar-Sql "delete from app.usuario where correo like 'e2e\_fase5\_%@prueba.local'"
    Write-Host "  sesiones: $sesiones · preguntas: $preguntas · usuarios: $usuarios · cargo de prueba eliminado"
}

Mostrar-Resumen
