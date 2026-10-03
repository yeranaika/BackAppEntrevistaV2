<#
.SYNOPSIS
    Prueba de punta a punta de la Fase 3 (banco de preguntas e IA): servidor real + Postgres real.

.DESCRIPTION
    Recorre permisos, creación y validación de preguntas, listado con filtros, edición, revisión
    (aprobar/rechazar con trazabilidad de IA), lectura para usuarios sin solución, eliminación y las
    validaciones previas a llamar al LLM.
    Por defecto NO llama al LLM (cuesta dinero y envía datos a OpenAI). Con -ConIa genera 1 pregunta real.
    Crea un cargo, una skill y usuarios e2e_fase3_* y los borra al terminar.

.EXAMPLE
    pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_3_PREGUNTAS.ps1 -UrlBase http://127.0.0.1:8093
    pwsh PRUEBAS_E2E/PRUEBA_E2E_FASE_3_PREGUNTAS.ps1 -ConIa   # incluye una generación real con OpenAI
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
$correoAdmin = "e2e_fase3_admin_$sufijo@prueba.local"
$correoUsuario = "e2e_fase3_usuario_$sufijo@prueba.local"
$cargoId = [guid]::NewGuid().ToString()
$skillId = [guid]::NewGuid().ToString()
$BASE = '/api/v1/admin/preguntas'

function Login([string]$Correo) { (Llamar-Api POST '/auth/login' @{ email = $Correo; password = $CONTRASENA }).Json.accessToken }

function Opcion([string]$Texto, [bool]$Correcta = $false) { @{ texto = $Texto; esCorrecta = $Correcta; explicacion = "Explicación de $Texto" } }

function Pregunta-OpcionMultiple([string]$Enunciado = '¿Qué hace la palabra clave suspend en Kotlin?', $Opciones = $null) {
    if ($null -eq $Opciones) {
        $Opciones = @((Opcion 'Permite suspender sin bloquear el hilo' $true), (Opcion 'Crea un hilo nuevo'), (Opcion 'Bloquea el hilo actual'))
    }
    @{ skillId = $skillId; tipo = 'opcion_multiple'; categoria = 'tecnica'; nivel = 'junior'; enunciado = $Enunciado; opciones = $Opciones }
}

<# Copia la tabla y reemplaza los campos indicados (sumar hashtables con claves repetidas falla). #>
function Con([hashtable]$Tabla, [hashtable]$Cambios) {
    $copia = $Tabla.Clone()
    foreach ($clave in $Cambios.Keys) { $copia[$clave] = $Cambios[$clave] }
    return $copia
}

function Contar-Generaciones { [int](Ejecutar-Sql "select count(*) from app.pregunta_generacion_ia where cargo_id = '$cargoId' or skill_id = '$skillId'") }

Write-Host "Prueba E2E Fase 3 (preguntas) contra $UrlBase"

try {
    Seccion 'Preparación'
    Llamar-Api POST '/auth/register' @{ email = $correoAdmin; password = $CONTRASENA } | Out-Null
    Llamar-Api POST '/auth/register' @{ email = $correoUsuario; password = $CONTRASENA } | Out-Null
    Ejecutar-Sql "update app.usuario set rol = 'admin' where correo = '$correoAdmin'" | Out-Null
    Ejecutar-Sql "insert into app.cargo (cargo_id, nombre, area, nivel_base) values ('$cargoId', 'E2E Cargo $sufijo', 'backend', 'semisenior')" | Out-Null
    Ejecutar-Sql "insert into app.skill (skill_id, nombre, categoria, tipo_area) values ('$skillId', 'E2E Skill $sufijo', 'tecnica', 'backend')" | Out-Null
    $admin = Login $correoAdmin
    $usuario = Login $correoUsuario
    $adminId = (Llamar-Api GET '/me' -Token $admin).Json.id
    Probar 'admin, usuario, cargo y skill de prueba listos' { $admin -and $usuario -and $adminId }

    Seccion 'Permisos'
    Probar 'sin token → 401' { (Llamar-Api GET $BASE).Estado -eq 401 }
    Probar 'usuario normal → 403' { (Llamar-Api GET $BASE -Token $usuario).Estado -eq 403 }
    Probar 'usuario normal no puede generar con IA → 403' { (Llamar-Api POST "$BASE/generar-ia" @{} -Token $usuario).Estado -eq 403 }

    Seccion 'Crear preguntas'
    $creada = Llamar-Api POST $BASE (Pregunta-OpcionMultiple) -Token $admin
    $idOpcionMultiple = $creada.Json.id
    Probar 'crear opción múltiple → 201, nace aprobada' { $creada.Estado -eq 201 -and $creada.Json.estado -eq 'aprobada' }
    Probar 'las opciones conservan el orden y hay una sola correcta' {
        ($creada.Json.opciones.orden -join ',') -eq '1,2,3' -and @($creada.Json.opciones | Where-Object esCorrecta).Count -eq 1
    }
    Probar 'Postgres acepta los valores (CHECK de tipo, categoría y nivel)' {
        (Ejecutar-Sql "select tipo_pregunta || '|' || categoria_habilidad || '|' || nivel_dificultad || '|' || estado from app.pregunta where pregunta_id = '$idOpcionMultiple'") -eq 'opcion_multiple|tecnica|junior|aprobada'
    }
    Probar 'las 3 opciones quedaron en opcion_pregunta' {
        (Ejecutar-Sql "select count(*) from app.opcion_pregunta where pregunta_id = '$idOpcionMultiple'") -eq '3'
    }

    $abierta = Llamar-Api POST $BASE @{
        cargoId = $cargoId; tipo = 'abierta_texto'; categoria = 'blanda'; nivel = 'senior'
        enunciado = 'Cuéntame de un conflicto en tu equipo y cómo lo resolviste'
        respuestaIdeal = 'Situación: dos áreas en conflicto. Tarea: alinear prioridades. Acción: facilité una reunión con datos. Resultado: acuerdo y entrega a tiempo.'
    } -Token $admin
    $idAbierta = $abierta.Json.id
    Probar 'crear pregunta abierta con respuesta ideal → 201' { $abierta.Estado -eq 201 }

    Seccion 'Validaciones (400/404)'
    $dosCorrectas = Llamar-Api POST $BASE (Pregunta-OpcionMultiple -Opciones @((Opcion 'A' $true), (Opcion 'B' $true))) -Token $admin
    Probar 'dos opciones correctas → 400 debe_haber_una_correcta' { $dosCorrectas.Estado -eq 400 -and $dosCorrectas.Json.error -eq 'debe_haber_una_correcta' }
    $unaOpcion = Llamar-Api POST $BASE (Pregunta-OpcionMultiple -Opciones @((Opcion 'A' $true))) -Token $admin
    Probar 'una sola opción → 400 opciones_invalidas' { $unaOpcion.Json.error -eq 'opciones_invalidas' }
    $sinRespuesta = Llamar-Api POST $BASE @{ skillId = $skillId; tipo = 'abierta_texto'; categoria = 'blanda'; nivel = 'junior'; enunciado = 'X' } -Token $admin
    Probar 'abierta sin respuesta ideal ni rúbrica → 400' { $sinRespuesta.Json.error -eq 'respuesta_ideal_o_rubrica_requerida' }
    $tipoMalo = Llamar-Api POST $BASE (Con (Pregunta-OpcionMultiple) @{ tipo = 'verdadero_falso' }) -Token $admin
    Probar 'tipo inválido → 400 tipo_invalido' { $tipoMalo.Json.error -eq 'tipo_invalido' }
    $skillInexistente = Llamar-Api POST $BASE (Con (Pregunta-OpcionMultiple) @{ skillId = [guid]::NewGuid().ToString() }) -Token $admin
    Probar 'skill inexistente → 404 skill_no_encontrada' { $skillInexistente.Estado -eq 404 -and $skillInexistente.Json.error -eq 'skill_no_encontrada' }
    Probar 'id con formato inválido → 400 id_invalido' { (Llamar-Api GET "$BASE/no-es-uuid" -Token $admin).Json.error -eq 'id_invalido' }

    Seccion 'Listar con filtros'
    1..3 | ForEach-Object { Llamar-Api POST $BASE (Pregunta-OpcionMultiple -Enunciado "Pregunta de paginación $_") -Token $admin | Out-Null }
    $pagina1 = Llamar-Api GET "$BASE`?skillId=$skillId&tipo=opcion_multiple&tamano=2&pagina=1" -Token $admin
    $pagina3 = Llamar-Api GET "$BASE`?skillId=$skillId&tipo=opcion_multiple&tamano=2&pagina=2" -Token $admin
    Probar 'filtra por skill y tipo: 4 en total, de a 2 por página' {
        $pagina1.Json.total -eq 4 -and @($pagina1.Json.elementos).Count -eq 2 -and @($pagina3.Json.elementos).Count -eq 2
    }
    Probar 'filtro por cargo trae solo la abierta' { (Llamar-Api GET "$BASE`?cargoId=$cargoId" -Token $admin).Json.total -eq 1 }
    Probar 'estado inválido en el filtro → 400' { (Llamar-Api GET "$BASE`?estado=borrador" -Token $admin).Json.error -eq 'estado_invalido' }

    Seccion 'Edición y revisión'
    $editada = Llamar-Api PUT "$BASE/$idOpcionMultiple" (Pregunta-OpcionMultiple -Enunciado '¿Para qué sirve suspend?') -Token $admin
    Probar 'editar cambia el enunciado y la devuelve a pendiente' { $editada.Json.enunciado -eq '¿Para qué sirve suspend?' -and $editada.Json.estado -eq 'pendiente' }
    $vistaUsuario = Llamar-Api GET "/api/v1/preguntas?skillId=$skillId&cantidad=20" -Token $usuario
    Probar 'el usuario no ve la pregunta mientras está pendiente' { -not (@($vistaUsuario.Json) | Where-Object id -eq $idOpcionMultiple) }

    $sinMotivo = Llamar-Api PATCH "$BASE/$idOpcionMultiple/rechazar" @{ motivo = '  ' } -Token $admin
    Probar 'rechazar sin motivo → 400 motivo_requerido' { $sinMotivo.Json.error -eq 'motivo_requerido' }
    $rechazada = Llamar-Api PATCH "$BASE/$idOpcionMultiple/rechazar" @{ motivo = 'Muy obvia' } -Token $admin
    Probar 'rechazar guarda el motivo' { $rechazada.Json.estado -eq 'rechazada' -and $rechazada.Json.motivoRechazo -eq 'Muy obvia' }
    $aprobada = Llamar-Api PATCH "$BASE/$idOpcionMultiple/aprobar" -Token $admin
    Probar 'aprobar la publica y limpia el motivo' { $aprobada.Json.estado -eq 'aprobada' -and -not $aprobada.Json.motivoRechazo }

    Seccion 'Preguntas generadas por IA (revisión y trazabilidad)'
    # Simula lo que deja una generación: pregunta pendiente + traza, sin pagar una llamada al LLM.
    $idIa = [guid]::NewGuid().ToString()
    Ejecutar-Sql ("insert into app.pregunta (pregunta_id, skill_id, tipo_pregunta, categoria_habilidad, nivel_dificultad, enunciado, respuesta_ideal, generada_por_ia, estado) " +
        "values ('$idIa', '$skillId', 'abierta_texto', 'tecnica', 'semisenior', 'Pregunta IA E2E', 'Respuesta ideal', true, 'pendiente')") | Out-Null
    Ejecutar-Sql ("insert into app.pregunta_generacion_ia (pregunta_id, skill_id, nivel_solicitado, modelo_llm, prompt_enviado, parse_exitoso, estado_revision) " +
        "values ('$idIa', '$skillId', 'semisenior', 'gpt-4o-mini', 'prompt e2e', true, 'pendiente_revision')") | Out-Null
    $pendientesIa = Llamar-Api GET "$BASE`?generadaPorIa=true&estado=pendiente&skillId=$skillId" -Token $admin
    Probar 'el admin encuentra las pendientes de IA con el filtro' { $pendientesIa.Json.total -eq 1 -and $pendientesIa.Json.elementos[0].id -eq $idIa }
    Llamar-Api PATCH "$BASE/$idIa/aprobar" -Token $admin | Out-Null
    Probar 'aprobarla marca la traza como aprobada y registra quién revisó' {
        (Ejecutar-Sql "select estado_revision || '|' || revisado_por from app.pregunta_generacion_ia where pregunta_id = '$idIa'") -eq "aprobada|$adminId"
    }

    Seccion 'Lectura para usuarios'
    $paraUsuario = Llamar-Api GET "/api/v1/preguntas?skillId=$skillId&cantidad=20" -Token $usuario
    Probar 'sin token → 401' { (Llamar-Api GET '/api/v1/preguntas').Estado -eq 401 }
    $idsVistos = @($paraUsuario.Json.id)
    $aprobadasEnBd = [int](Ejecutar-Sql "select count(*) from app.pregunta where skill_id = '$skillId' and estado = 'aprobada'")
    Probar "el usuario recibe exactamente las $aprobadasEnBd aprobadas de la skill (incluida la de IA recién aprobada)" {
        $idsVistos.Count -eq $aprobadasEnBd -and $idsVistos -contains $idIa -and $idsVistos -contains $idOpcionMultiple
    }
    Probar 'ninguna de las que recibe está pendiente ni rechazada en la BD' {
        $lista = ($idsVistos | ForEach-Object { "'$_'" }) -join ','
        (Ejecutar-Sql "select count(*) from app.pregunta where pregunta_id in ($lista) and estado <> 'aprobada'") -eq '0'
    }
    Probar 'nunca recibe la solución (respuesta ideal, opción correcta ni explicación)' {
        -not ($paraUsuario.Texto -match 'respuestaIdeal|esCorrecta|explicacion|rubrica')
    }
    Probar 'cantidad fuera de rango → 400' { (Llamar-Api GET '/api/v1/preguntas?cantidad=99' -Token $usuario).Json.error -eq 'cantidad_invalida' }

    Seccion 'Eliminar'
    Ejecutar-Sql "update app.pregunta set veces_usada = 1 where pregunta_id = '$idAbierta'" | Out-Null
    $enUso = Llamar-Api DELETE "$BASE/$idAbierta" -Token $admin
    Probar 'una pregunta ya usada no se borra → 409 pregunta_en_uso' { $enUso.Estado -eq 409 -and $enUso.Json.error -eq 'pregunta_en_uso' }
    Probar 'una pregunta sin uso se borra junto a sus opciones' {
        (Llamar-Api DELETE "$BASE/$idOpcionMultiple" -Token $admin).Estado -eq 200 -and
            (Ejecutar-Sql "select count(*) from app.opcion_pregunta where pregunta_id = '$idOpcionMultiple'") -eq '0'
    }

    Seccion 'Generación con IA: validaciones antes de llamar al LLM'
    $antes = Contar-Generaciones
    $cargoInexistente = Llamar-Api POST "$BASE/generar-ia" @{ cargo_id = [guid]::NewGuid().ToString(); nivel = 'senior'; cantidad = 1 } -Token $admin
    Probar 'cargo inexistente → 404 cargo_no_encontrado (antes costaba una llamada)' { $cargoInexistente.Estado -eq 404 -and $cargoInexistente.Json.error -eq 'cargo_no_encontrado' }
    $sinContexto = Llamar-Api POST "$BASE/generar-ia" @{ nivel = 'senior'; cantidad = 1 } -Token $admin
    Probar 'sin cargo ni skill → 400 contexto_requerido' { $sinContexto.Json.error -eq 'contexto_requerido' }
    Probar 'más de 10 preguntas → 400 cantidad_invalida' {
        (Llamar-Api POST "$BASE/generar-ia" @{ cargo_id = $cargoId; nivel = 'senior'; cantidad = 11 } -Token $admin).Json.error -eq 'cantidad_invalida'
    }
    Probar 'modelo no soportado → 400 modelo_invalido' {
        (Llamar-Api POST "$BASE/generar-ia" @{ cargo_id = $cargoId; nivel = 'senior'; modelo = 'gpt-9' } -Token $admin).Json.error -eq 'modelo_invalido'
    }
    Probar 'la ruta antigua /questions/generate-ai valida igual' {
        (Llamar-Api POST '/api/v1/admin/questions/generate-ai' @{ nivel = 'senior' } -Token $admin).Json.error -eq 'contexto_requerido'
    }
    Probar 'ninguna de esas solicitudes llegó al LLM (sin trazas nuevas)' { (Contar-Generaciones) -eq $antes }

    if ($ConIa) {
        Seccion 'Generación real con IA (-ConIa)'
        $generada = Llamar-Api POST "$BASE/generar-ia" @{ cargo_id = $cargoId; skill_id = $skillId; nivel = 'junior'; cantidad = 1; tipo = 'opcion_multiple'; categoria = 'tecnica' } -Token $admin
        Probar "genera 1 pregunta (estado HTTP $($generada.Estado): $($generada.Json.error))" { $generada.Estado -eq 201 -and $generada.Json.preguntas_generadas -eq 1 }
        Probar 'queda pendiente con su traza y costo' {
            $id = $generada.Json.preguntas[0].pregunta_id
            (Ejecutar-Sql "select p.estado || '|' || g.parse_exitoso || '|' || (g.costo_usd > 0) from app.pregunta p join app.pregunta_generacion_ia g using (pregunta_id) where p.pregunta_id = '$id'") -eq 'pendiente|t|t'
        }
        Probar 'el prompt enviado incluye el cargo y la skill' {
            (Ejecutar-Sql "select count(*) from app.pregunta_generacion_ia where cargo_id = '$cargoId' and prompt_enviado like '%E2E Cargo $sufijo%' and prompt_enviado like '%E2E Skill $sufijo%'") -ne '0'
        }
    }
}
finally {
    Seccion 'Limpieza'
    Ejecutar-Sql "delete from app.pregunta_generacion_ia where cargo_id = '$cargoId' or skill_id = '$skillId'" | Out-Null
    $preguntas = Ejecutar-Sql "delete from app.pregunta where cargo_id = '$cargoId' or skill_id = '$skillId'"
    Ejecutar-Sql "delete from app.cargo where cargo_id = '$cargoId'" | Out-Null
    Ejecutar-Sql "delete from app.skill where skill_id = '$skillId'" | Out-Null
    $usuarios = Ejecutar-Sql "delete from app.usuario where correo like 'e2e\_fase3\_%@prueba.local'"
    Write-Host "  preguntas: $preguntas · usuarios: $usuarios · cargo y skill de prueba eliminados"
}

Mostrar-Resumen
