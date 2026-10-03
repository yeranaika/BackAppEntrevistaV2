"""
Genera la documentación de la API (API.md) y la colección y el entorno de Postman desde una sola definición.

    python docs/documentacion/GENERAR_DOCUMENTACION.py
    python docs/documentacion/GENERAR_DOCUMENTACION.py --probar URL CORREO CONTRASENA CORREO_ADMIN CONTRASENA_ADMIN

Al agregar o cambiar un endpoint, editar CARPETAS y volver a ejecutar: API.md y Postman quedan iguales.

Cada request define su método, ruta, autenticación, body de ejemplo y qué variables guarda de la respuesta.
Con eso se generan los scripts de Postman (pm.collectionVariables.set) y también el modo --probar,
que recorre las requests en orden contra el servidor para comprobar que la colección funciona.
Las requests marcadas como `omitir_en_prueba` cuestan dinero, mandan correos o borran la cuenta.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import uuid

CARPETA = os.path.dirname(os.path.abspath(__file__))
CARPETA_POSTMAN = os.path.join(CARPETA, "postman")
CODIGO_FUENTE = os.path.join(CARPETA, "..", "..", "src", "main", "kotlin")
NOMBRE = "EntrevistaAPP API"

# ─── Variables de la colección ───────────────────────────────────────────────
VARIABLES = {
    "baseUrl": "http://localhost:8080",
    "correo": "usuario.prueba@ejemplo.com",
    "contrasena": "Clave-segura-1",
    "correoAdmin": "admin@ejemplo.com",
    "contrasenaAdmin": "Clave-segura-1",
    "accessToken": "", "refreshToken": "", "adminToken": "",
    "usuarioId": "", "cargoId": "", "nombreCargo": "", "skillId": "", "preguntaId": "",
    "sesionId": "", "preguntaSesionId": "", "opcionId": "",
    "practicaId": "", "preguntaPracticaId": "", "opcionPracticaId": "",
    "intentoId": "", "preguntaNivelacionId": "", "opcionNivelacionId": "",
    "testNivelacionId": "", "pruebaId": "", "preguntaAppId": "", "opcionAppId": "",
    "versionConsentimiento": "", "codigoPremium": "",
}

USUARIO, ADMIN, PUBLICA = "usuario", "admin", "publica"


def req(nombre, metodo, ruta, auth=USUARIO, body=None, desc="", guarda=None, consulta=None,
        espera=None, omitir_en_prueba=False):
    """guarda: {variable: (expresión JS sobre `j`, función Python sobre el JSON)}"""
    return dict(nombre=nombre, metodo=metodo, ruta=ruta, auth=auth, body=body, desc=desc.strip(),
                guarda=guarda or {}, consulta=consulta or [], espera=espera or [200], omitir=omitir_en_prueba)


def primero_de_alternativas(lista, campo_id, campo_opciones, campo_opcion_id):
    """Para el modo --probar: la primera pregunta de la lista y su primera opción (vacía si es abierta)."""
    if not lista:
        return "", ""
    p = lista[0]
    opciones = p.get(campo_opciones) or []
    return p.get(campo_id, ""), (opciones[0].get(campo_opcion_id, "") if opciones else "")


CARPETAS = [
    ("00 · Sistema", "Estado del servicio.", [
        req("Salud", "GET", "/health", PUBLICA, desc="""
Responde `OK` si la base de datos responde y `503 db_no_disponible` si no. Lo usa el supervisor / balanceador."""),
    ]),
    ("01 · Autenticación", "Registro, login con correo o Google, rotación y cierre de sesión. "
     "Los requests de login guardan `accessToken` y `refreshToken` automáticamente.", [
        req("Registrar usuario", "POST", "/auth/register", PUBLICA,
            body={"email": "{{correo}}", "password": "{{contrasena}}", "nombre": "Usuario Prueba", "nivelExperiencia": "junior", "area": "TI"},
            espera=[201, 409],
            guarda={"accessToken": ("j.accessToken", lambda j: j.get("accessToken")), "refreshToken": ("j.refreshToken", lambda j: j.get("refreshToken"))},
            desc="""
Crea la cuenta (contraseña Argon2id, mínimo 8 caracteres) y devuelve tokens.
Errores con el formato antiguo que lee Android: `409 {"error":"email_in_use"}` y `422 {"error":"<código>"}` (ej. `area_invalida`, `nivel_experiencia_invalido`, `invalid_country`, `invalid_birthdate`).
Valores válidos de `area` (exactos, como los envía la app): `TI`, `Desarollador`, `Analista`, `Administracion`, `Otra área`, `Ventas / Comercial`, `Finanzas`, `RRHH / Personas`, `Diseño / UX`, `Operaciones / Logística`. Otro valor → `area_invalida`.
Límite: 30 registros cada 10 minutos por IP → `429 demasiadas_solicitudes`."""),
        req("Login", "POST", "/auth/login", PUBLICA, body={"email": "{{correo}}", "password": "{{contrasena}}"},
            guarda={"accessToken": ("j.accessToken", lambda j: j.get("accessToken")), "refreshToken": ("j.refreshToken", lambda j: j.get("refreshToken"))},
            desc="""
Devuelve `accessToken` (JWT, 15 min) y `refreshToken` (15 días).
`401 bad_credentials`; tras 5 contraseñas incorrectas el correo queda bloqueado 15 min → `429 demasiados_intentos`."""),
        req("Login admin", "POST", "/auth/login", PUBLICA, body={"email": "{{correoAdmin}}", "password": "{{contrasenaAdmin}}"},
            guarda={"adminToken": ("j.accessToken", lambda j: j.get("accessToken"))},
            desc="Igual que Login, pero guarda el token en `adminToken`. La cuenta debe tener rol `admin` (se asigna desde Administración de usuarios o en la BD)."),
        req("Renovar tokens", "POST", "/auth/refresh", PUBLICA, body={"refreshToken": "{{refreshToken}}"},
            guarda={"accessToken": ("j.accessToken", lambda j: j.get("accessToken")), "refreshToken": ("j.refreshToken", lambda j: j.get("refreshToken"))},
            desc="Rota el refresh token (el anterior deja de servir). Reusar uno ya rotado revoca todas las sesiones del usuario → `401`."),
        req("Login con Google (Android)", "POST", "/auth/google", PUBLICA, body={"idToken": "<id token de Google>"}, omitir_en_prueba=True,
            desc="Recibe el `idToken` que obtiene la app con Google Sign-In y devuelve los tokens de la API."),
        req("Login con Google (web)", "GET", "/auth/google/start", PUBLICA, omitir_en_prueba=True,
            desc="Redirige al consentimiento de Google; al volver (`/auth/google/callback`) entrega los tokens. Abrir en el navegador, no en Postman."),
    ]),
    ("02 · Contraseña", "Recuperación por código enviado al correo y cambio desde el perfil.", [
        req("Olvidé mi contraseña", "POST", "/auth/forgot-password", PUBLICA, body={"correo": "{{correo}}"}, omitir_en_prueba=True,
            desc="⚠️ Envía un correo real. Responde lo mismo exista o no la cuenta. Límite: 5 cada 15 min por IP."),
        req("Restablecer con código", "POST", "/auth/reset-password", PUBLICA,
            body={"correo": "{{correo}}", "codigo": "123456", "nuevaContrasena": "Otra-clave-segura-2"}, omitir_en_prueba=True,
            desc="Valida el código de 6 dígitos (15 min, intentos limitados) y cambia la contraseña; cierra las demás sesiones."),
        req("Cambiar contraseña", "POST", "/auth/change-password", USUARIO,
            body={"contrasenaActual": "{{contrasena}}", "nuevaContrasena": "{{contrasena}}"}, espera=[200, 400],
            desc="Requiere la contraseña actual. `400` si la nueva es igual a la actual o no cumple el mínimo.\nEl ejemplo usa **la misma** contraseña a propósito (responde `400`) para no cambiar la de la variable `contrasena`. Para cambiarla de verdad: poner otra en `nuevaContrasena` y luego actualizar la variable `contrasena`."),
    ]),
    ("03 · Cuenta y perfil", "", [
        req("Mi cuenta", "GET", "/me", guarda={"usuarioId": ("j.id", lambda j: j.get("id"))},
            desc="Cuenta + perfil + cargo meta. Guarda `usuarioId`."),
        req("Actualizar cuenta", "PUT", "/me", body={"nombre": "Usuario Prueba", "idioma": "es", "telefono": "+56912345678", "fechaNacimiento": "1998-05-20", "genero": "otro"}),
        req("Mi perfil", "GET", "/me/perfil", espera=[200, 404]),
        req("Actualizar perfil", "PUT", "/me/perfil", desc="Valores válidos de `area` (exactos, como los envía la app): `TI`, `Desarollador`, `Analista`, `Administracion`, `Otra área`, `Ventas / Comercial`, `Finanzas`, `RRHH / Personas`, `Diseño / UX`, `Operaciones / Logística`. Otro valor → `area_invalida`.", body={"nivelExperiencia": "junior", "area": "TI", "pais": "CL", "notaObjetivos": "Conseguir mi primer trabajo"}),
        req("Eliminar mi cuenta", "DELETE", "/cuenta", body={"confirmar": "eliminar"}, omitir_en_prueba=True,
            desc="⚠️ Borrado definitivo de la cuenta y todos sus datos. Requiere `{\"confirmar\": \"eliminar\"}`."),
    ]),
    ("04 · Onboarding y objetivo", "", [
        req("Onboarding (Android)", "PUT", "/perfil/objetivo", body={"area": "TI", "metaCargo": "Backend Developer", "nivel": "jr"},
            desc="Área, cargo meta y nivel (acepta `jr|mid|sr` o `junior|semisenior|senior`). Valores válidos de `area` (exactos, como los envía la app): `TI`, `Desarollador`, `Analista`, `Administracion`, `Otra área`, `Ventas / Comercial`, `Finanzas`, `RRHH / Personas`, `Diseño / UX`, `Operaciones / Logística`. Otro valor → `area_invalida`."),
        req("Onboarding", "POST", "/onboarding", body={"area": "TI", "nivelExperiencia": "junior", "nombreCargo": "Backend Developer", "descripcionObjetivo": "Primer empleo"}),
        req("Ver onboarding", "GET", "/onboarding"),
        req("Estado del onboarding", "GET", "/onboarding/status"),
        req("Mi objetivo", "GET", "/me/objetivo", espera=[200, 404]),
        req("Cambiar objetivo", "PUT", "/me/objetivo", body={"nombreCargo": "Backend Developer", "sector": "tecnologia"}),
        req("Borrar objetivo", "DELETE", "/me/objetivo", omitir_en_prueba=True, desc="Desactiva el cargo meta (la entrevista y la práctica lo usan por defecto)."),
    ]),
    ("05 · Consentimientos y documentos legales", "", [
        req("Texto vigente", "GET", "/consent/current", PUBLICA, guarda={"versionConsentimiento": ("j.version", lambda j: j.get("version"))}),
        req("Aceptar consentimiento", "POST", "/me/consent", body={"version": "{{versionConsentimiento}}", "alcances": {"uso_datos": True, "ia_entrenamiento": False}},
            espera=[201], desc="`404 version_no_encontrada` si la versión no existe. Un consentimiento nuevo revoca el anterior."),
        req("Mi consentimiento", "GET", "/me/consent/latest", espera=[200, 204]),
        req("Revocar consentimiento", "POST", "/me/consent/revoke", espera=[200, 404]),
        req("EULA vigente", "GET", "/api/v1/legal/eula", PUBLICA),
        req("Versiones del EULA", "GET", "/api/v1/legal/versions", PUBLICA),
        req("Términos de servicio", "GET", "/api/v1/legal/terms", PUBLICA),
        req("Política de privacidad", "GET", "/api/v1/legal/privacy", PUBLICA),
        req("Publicar versión del EULA (admin)", "POST", "/api/v1/legal/admin/eula", ADMIN,
            body={"version": "2.0.0", "title": "EULA", "body": "Texto"}, omitir_en_prueba=True,
            desc="⚠️ Cambia el texto vigente para todos los usuarios. Alias: `POST /admin/consent/text`."),
    ]),
    ("06 · Recordatorios", "", [
        req("Guardar preferencias", "PUT", "/recordatorios/preferencias",
            body={"diasSemana": ["LUN", "MIE", "VIE"], "hora": "20:30", "tipoPractica": "entrevista", "habilitado": True},
            desc="Días `LUN..DOM` (también acepta nombres completos), hora `HH:mm`, `tipoPractica` hasta 32 caracteres."),
        req("Ver preferencias", "GET", "/recordatorios/preferencias", desc="`404 recordatorio_no_configurado` si nunca se guardaron."),
    ]),
    ("07 · Suscripción (billing)", "JSON en snake_case (contrato de Android).", [
        req("Estado de la suscripción", "GET", "/billing/status"),
        req("Crear código premium (admin)", "POST", "/billing/admin/codes", ADMIN,
            body={"days": 30, "label": "postman", "max_uses": 1, "license_type": "PROM"}, espera=[201],
            guarda={"codigoPremium": ("j.code", lambda j: j.get("code"))}, desc="`license_type`: PROM, INST o GOOG."),
        req("Canjear código", "POST", "/billing/code/redeem", body={"code": "{{codigoPremium}}"}, espera=[200, 400],
            desc="Atómico: nunca supera `max_uses`. `400 codigo_invalido_o_expirado`."),
        req("Verificar compra de Google Play", "POST", "/billing/google/verify",
            body={"product_id": "premium_mensual", "purchase_token": "<token de Google Play>", "purchase_time": 1735689600000}, omitir_en_prueba=True,
            desc="Un token pertenece a una sola cuenta (`409 compra_ya_registrada`). Google caído → `503`. Con `GOOGLE_PLAY_BILLING_MOCK=true` se simula."),
    ]),
    ("08 · Mercado laboral", "Catálogo público de cargos y skills (caché Redis: cargos 6 h, matriz 12 h; crear un cargo invalida la caché). Las tendencias se sincronizan solas cada 7 días desde JSearch → Remotive → Arbeitnow → dataset de contingencia. La administración es solo para admin.", [
        req("Crear cargo (admin)", "POST", "/admin/market/cargos", ADMIN,
            body={"nombre": "Cargo Postman {{$timestamp}}", "area": "backend", "descripcion": "Creado desde Postman", "nivelBase": "junior", "autoGenerateSkills": False},
            espera=[201], guarda={"cargoId": ("j.cargo.cargoId", lambda j: j["cargo"]["cargoId"]), "nombreCargo": ("j.cargo.nombre", lambda j: j["cargo"]["nombre"])},
            desc="Con `autoGenerateSkills: true` arma los requisitos desde las APIs de empleo (⚠️ consume cuota)."),
        req("Cargos", "GET", "/api/v1/cargos", PUBLICA, desc="Alias antiguo: `GET /market/cargos`."),
        req("Matriz de skills del cargo", "GET", "/api/v1/cargos/{{cargoId}}/skills", PUBLICA),
        req("Skills en tendencia", "GET", "/api/v1/skills/trending", PUBLICA, consulta=[("categoria", "tecnica"), ("limit", "10")]),
        req("Skills por demanda", "GET", "/market/skills", PUBLICA,
            guarda={"skillId": ("j.length ? j[0].skillId : ''", lambda j: j[0]["skillId"] if j else "")}),
        req("Historial de una skill", "GET", "/market/skills/{{skillId}}/tendencias", PUBLICA, espera=[200, 400, 404]),
        req("Sincronizar tendencias (admin)", "POST", "/admin/market/sync-trends", ADMIN, omitir_en_prueba=True,
            desc="⚠️ Llama a las APIs de empleo (consume cuota). Corre sola cada 7 días."),
        req("Regenerar requisitos de un cargo (admin)", "POST", "/admin/market/cargos/{{cargoId}}/generate-requirements", ADMIN, omitir_en_prueba=True,
            desc="⚠️ Consume cuota de las APIs de empleo. `POST /admin/market/cargos/generate-all` hace lo mismo con todos."),
        req("Regenerar requisitos de todos los cargos (admin)", "POST", "/admin/market/cargos/generate-all", ADMIN, omitir_en_prueba=True,
            desc="⚠️ Consume cuota de las APIs de empleo por cada cargo."),
    ]),
    ("09 · Banco de preguntas", "Administración (solo admin) y lectura para usuarios.", [
        req("Crear pregunta de alternativas (admin)", "POST", "/api/v1/admin/preguntas", ADMIN,
            body={"cargoId": "{{cargoId}}", "tipo": "opcion_multiple", "categoria": "tecnica", "nivel": "junior",
                  "enunciado": "¿Qué hace la palabra clave suspend en Kotlin? {{$timestamp}}",
                  "opciones": [{"texto": "Permite suspender sin bloquear el hilo", "esCorrecta": True, "explicacion": "Las corrutinas se suspenden sin bloquear"},
                               {"texto": "Crea un hilo nuevo"}, {"texto": "Bloquea el hilo actual"}]},
            espera=[201], guarda={"preguntaId": ("j.id", lambda j: j.get("id"))},
            desc="Nace aprobada. Opción múltiple: 2 a 6 opciones y exactamente una correcta. Requiere `cargoId` o `skillId`."),
        req("Crear otra pregunta de alternativas (admin)", "POST", "/api/v1/admin/preguntas", ADMIN,
            body={"cargoId": "{{cargoId}}", "tipo": "opcion_multiple", "categoria": "tecnica", "nivel": "junior",
                  "enunciado": "¿Qué diferencia a val de var? {{$timestamp}}",
                  "opciones": [{"texto": "val no se puede reasignar", "esCorrecta": True}, {"texto": "No hay diferencia"}]}, espera=[201]),
        req("Crear pregunta abierta (admin)", "POST", "/api/v1/admin/preguntas", ADMIN,
            body={"cargoId": "{{cargoId}}", "tipo": "abierta_texto", "categoria": "tecnica", "nivel": "junior",
                  "enunciado": "Explica qué es una corrutina {{$timestamp}}",
                  "respuestaIdeal": "Una corrutina se suspende sin bloquear el hilo y se reanuda después.",
                  "rubrica": {"criterios": ["explica_suspension"], "palabras_clave": ["corrutina", "hilo"]}}, espera=[201],
            desc="Las abiertas necesitan `respuestaIdeal` o `rubrica`. Las `palabras_clave` de la rúbrica las usa el motor freemium."),
        req("Crear pregunta de comportamiento (admin)", "POST", "/api/v1/admin/preguntas", ADMIN,
            body={"cargoId": "{{cargoId}}", "tipo": "abierta_texto", "categoria": "blanda", "nivel": "junior",
                  "enunciado": "Cuéntame de un conflicto en tu equipo {{$timestamp}}",
                  "respuestaIdeal": "Situación, tarea, acción y resultado del conflicto."}, espera=[201]),
        req("Listar preguntas (admin)", "GET", "/api/v1/admin/preguntas", ADMIN,
            consulta=[("estado", "aprobada"), ("cargoId", "{{cargoId}}"), ("pagina", "1"), ("tamano", "20")],
            desc="Filtros: estado, tipo, categoria, nivel, skillId, cargoId, generadaPorIa, pagina, tamano."),
        req("Ver pregunta (admin)", "GET", "/api/v1/admin/preguntas/{{preguntaId}}", ADMIN),
        req("Aprobar pregunta (admin)", "PATCH", "/api/v1/admin/preguntas/{{preguntaId}}/aprobar", ADMIN),
        req("Editar pregunta (admin)", "PUT", "/api/v1/admin/preguntas/{{preguntaId}}", ADMIN,
            body={"cargoId": "{{cargoId}}", "tipo": "opcion_multiple", "categoria": "tecnica", "nivel": "junior",
                  "enunciado": "¿Para qué sirve suspend en Kotlin?",
                  "opciones": [{"texto": "Suspender sin bloquear el hilo", "esCorrecta": True}, {"texto": "Crear un hilo"}]},
            omitir_en_prueba=True, desc="Reemplaza el contenido; la pregunta vuelve a quedar pendiente de revisión."),
        req("Borrar pregunta (admin)", "DELETE", "/api/v1/admin/preguntas/{{preguntaId}}", ADMIN, omitir_en_prueba=True,
            desc="Solo si nunca se usó en una prueba; si no, conviene rechazarla."),
        req("Rechazar pregunta (admin)", "PATCH", "/api/v1/admin/preguntas/{{preguntaId}}/rechazar", ADMIN, body={"motivo": "Muy fácil"}, omitir_en_prueba=True,
            desc="Deja la pregunta fuera de las pruebas. `PUT /api/v1/admin/preguntas/{id}` edita (vuelve a pendiente) y `DELETE` borra si nunca se usó."),
        req("Generar preguntas con IA (admin)", "POST", "/api/v1/admin/preguntas/generar-ia", ADMIN,
            body={"cargo_id": "{{cargoId}}", "nivel": "junior", "cantidad": 2, "tipo": "abierta_texto", "categoria": "tecnica", "modelo": "gpt-4o-mini"},
            omitir_en_prueba=True, desc="⚠️ Llama al LLM (cuesta). Las preguntas nacen pendientes de revisión. Máximo 10."),
        req("Preguntas para usuarios", "GET", "/api/v1/preguntas", consulta=[("cargoId", "{{cargoId}}"), ("cantidad", "5")],
            desc="Solo aprobadas y sin la solución."),
    ]),
    ("10 · Entrevista", "Simulación pregunta a pregunta.\n\n- Una sola entrevista en curso por usuario (`409 entrevista_en_progreso`); una abandonada más de 2 h se cancela sola.\n- Preguntas aprobadas del nivel pedido: primero las del cargo, luego las de sus skills, luego generales; 60 % técnicas y 40 % blandas; evita repetir las de las últimas 3 entrevistas.\n- Cada pregunta guarda una copia fija (enunciado, tipo, opciones): editar o borrar el banco no cambia una entrevista rendida.\n- La corrección se muestra recién al terminar. Las entrevistas de otro usuario responden `404`.", [
        req("Iniciar entrevista", "POST", "/api/v1/entrevistas", body={"cargoId": "{{cargoId}}", "nivel": "junior", "cantidadPreguntas": 3},
            espera=[201], guarda={"sesionId": ("j.sesionId", lambda j: j.get("sesionId"))},
            desc="Sin `cargoId` acepta `cargo` (nombre) o usa el objetivo del onboarding. `409 entrevista_en_progreso`, `409 preguntas_insuficientes`."),
        req("Entrevista en curso", "GET", "/api/v1/entrevistas/actual", espera=[200, 204]),
        req("Siguiente pregunta", "GET", "/api/v1/entrevistas/{{sesionId}}/siguiente", espera=[200, 204],
            guarda={"preguntaSesionId": ("j.preguntaSesionId", lambda j: j.get("preguntaSesionId")),
                    "opcionId": ("(j.opciones && j.opciones.length) ? j.opciones[0].opcionId : ''",
                                 lambda j: (j.get("opciones") or [{}])[0].get("opcionId", ""))}),
        req("Responder pregunta", "POST", "/api/v1/entrevistas/{{sesionId}}/respuestas",
            body={"preguntaSesionId": "{{preguntaSesionId}}", "opcionId": "{{opcionId}}", "texto": "Una corrutina se suspende sin bloquear el hilo."},
            desc="En alternativas se usa `opcionId`; en abiertas `texto` (y `videoClipUrl` https en las de video). Cada pregunta se responde una vez."),
        req("Enviar métricas de video", "POST", "/api/v1/entrevistas/{{sesionId}}/metricas",
            body={"metricas": [{"timestampMs": 0, "contactoVisual": 80.5, "postura": 70, "confianza": 65, "expresion": "seguro"},
                               {"timestampMs": 500, "contactoVisual": 78, "postura": 72, "confianza": 66, "gestos": {"toca_cara": False}, "expresion": "neutral"}]},
            espera=[201], desc="Lote de 1 a 300 métricas (puntajes 0-100; expresión seguro|nervioso|distraido|neutral|confuso)."),
        req("Detalle de la entrevista", "GET", "/api/v1/entrevistas/{{sesionId}}", desc="La corrección se muestra recién al terminar."),
        req("Finalizar entrevista", "POST", "/api/v1/entrevistas/{{sesionId}}/finalizar",
            desc="Requiere al menos una respuesta. Lanza en segundo plano el reporte de feedback."),
        req("Historial de entrevistas", "GET", "/api/v1/entrevistas", consulta=[("pagina", "1"), ("tamano", "20")]),
        req("Cancelar entrevista", "POST", "/api/v1/entrevistas/{{sesionId}}/cancelar", espera=[200, 409], omitir_en_prueba=True,
            desc="Solo una entrevista en curso (`409 entrevista_no_activa` si ya terminó)."),
    ]),
    ("11 · Feedback", "Reporte de la entrevista (se genera en segundo plano al finalizarla) y progreso por skill.\n\n- **Puntaje global**: técnico 50 %, blando 30 %, lenguaje corporal 20 % (promedio de las métricas de video); si algo no se midió, su peso se reparte. Las preguntas sin responder cuentan 0.\n- **Respuestas abiertas**: motor freemium (sin costo) para todos; IA para usuarios premium si hay `OPENAI_API_KEY` o `ANTHROPIC_API_KEY`. Si la IA falla se usa el freemium: el reporte siempre sale.\n- Si la generación falla, el usuario ve un mensaje claro y `puedeReintentar`; el código del error queda solo en la BD.", [
        req("Reporte de la entrevista", "GET", "/api/v1/entrevistas/{{sesionId}}/reporte",
            desc="`estado`: generando | listo | error. Si es `generando`, volver a consultar en unos segundos. `puedeReintentar` indica si sirve reintentar."),
        req("Reintentar reporte", "POST", "/api/v1/entrevistas/{{sesionId}}/reporte/reintentar", espera=[202, 409],
            desc="Solo si terminó con error (hasta 3 intentos). `409 reporte_no_reintentable`."),
        req("Mis reportes", "GET", "/api/v1/reportes"),
        req("Mi progreso por skill", "GET", "/api/v1/me/progreso"),
    ]),
    ("12 · Práctica", "Rondas de preguntas escritas (sin video) con feedback inmediato.\n\n- Alternativas: se corrigen contra la opción correcta y devuelven su explicación. Abiertas: motor freemium; cuentan como correctas desde 60 puntos.\n- Una práctica en curso a la vez: empezar otra abandona la anterior.\n- Suma puntaje a cada skill en el progreso, pero no cambia su nivel (eso lo decide la nivelación).", [
        req("Iniciar práctica", "POST", "/api/v1/practicas", body={"cargoId": "{{cargoId}}", "modo": "mixto", "nivel": "junior", "cantidadPreguntas": 3},
            espera=[201], guarda={
                "practicaId": ("j.sesionId", lambda j: j.get("sesionId")),
                "preguntaPracticaId": ("j.preguntas[0].preguntaId", lambda j: primero_de_alternativas(j.get("preguntas"), "preguntaId", "opciones", "opcionId")[0]),
                "opcionPracticaId": ("(j.preguntas[0].opciones && j.preguntas[0].opciones.length) ? j.preguntas[0].opciones[0].opcionId : ''",
                                     lambda j: primero_de_alternativas(j.get("preguntas"), "preguntaId", "opciones", "opcionId")[1])},
            desc="Con `skillId` practica esa skill; si no, el cargo. `modo`: opcion_multiple | abierta_texto | mixto. Empezar otra abandona la anterior."),
        req("Responder (feedback inmediato)", "POST", "/api/v1/practicas/{{practicaId}}/respuestas",
            body={"preguntaId": "{{preguntaPracticaId}}", "opcionId": "{{opcionPracticaId}}", "texto": "Una corrutina se suspende sin bloquear el hilo.", "tiempoRespuestaMs": 4000},
            desc="Devuelve si es correcta, el puntaje (0-100), la opción correcta, la respuesta ideal y el feedback."),
        req("Detalle de la práctica", "GET", "/api/v1/practicas/{{practicaId}}"),
        req("Finalizar práctica", "POST", "/api/v1/practicas/{{practicaId}}/finalizar", desc="Promedio de lo respondido; suma puntaje a cada skill."),
        req("Mis prácticas", "GET", "/api/v1/practicas"),
        req("Historial de todas las pruebas", "GET", "/api/v1/pruebas/historial"),
        req("Sincronizar intentos offline", "POST", "/api/v1/sync/attempts",
            body={"attempts": [{"localAttemptId": "local-{{$timestamp}}", "skillId": "{{skillId}}", "modo": "abierta_texto", "categoria": "tecnica",
                                "nivelPreguntas": "junior", "fechaCreacionIso": "2026-01-15T10:00:00Z",
                                "respuestas": [{"preguntaId": "pregunta-offline-1", "enunciado": "¿Qué es Kotlin?", "respuestaTexto": "Un lenguaje", "esCorrecta": True, "puntaje": 8, "orden": 1}]}]},
            espera=[200, 400], desc="Idempotente por `localAttemptId`. Requiere un `skillId` existente (si no hay skills, `400 skill_no_encontrada`)."),
        req("Evaluar texto (freemium)", "POST", "/api/v1/practice/evaluate-freemium",
            body={"userText": "Un deadlock bloquea procesos", "idealText": "Un deadlock bloquea procesos que esperan recursos", "expectedKeywords": ["deadlock", "recursos"]}),
    ]),
    ("13 · Nivelación", "Test para saber el nivel actual y las brechas contra el cargo.\n\n- **Nivel**: se sube de junior a senior mientras el promedio del nivel llegue a 60; un nivel sin preguntas corta la subida.\n- **Brecha**: por cada skill evaluada contra el nivel que pide el cargo; prioridad alta si faltan 2 niveles o la skill es obligatoria.\n- No cambia el nivel del perfil: se informa como nivel sugerido.", [
        req("Iniciar nivelación", "POST", "/api/v1/nivelacion", body={"cargoId": "{{cargoId}}"}, espera=[201], guarda={
                "intentoId": ("j.intentoId", lambda j: j.get("intentoId")),
                "preguntaNivelacionId": ("j.preguntas[0].preguntaId", lambda j: primero_de_alternativas(j.get("preguntas"), "preguntaId", "opciones", "opcionId")[0]),
                "opcionNivelacionId": ("(j.preguntas[0].opciones && j.preguntas[0].opciones.length) ? j.preguntas[0].opciones[0].opcionId : ''",
                                       lambda j: primero_de_alternativas(j.get("preguntas"), "preguntaId", "opciones", "opcionId")[1])},
            desc="Usa el test del admin para el cargo o 3 técnicas por nivel desde el banco. `409 preguntas_insuficientes`."),
        req("Detalle de la nivelación", "GET", "/api/v1/nivelacion/{{intentoId}}"),
        req("Rendir nivelación", "POST", "/api/v1/nivelacion/{{intentoId}}/respuestas",
            body={"respuestas": [{"preguntaId": "{{preguntaNivelacionId}}", "opcionId": "{{opcionNivelacionId}}", "texto": "Una corrutina se suspende sin bloquear el hilo."}]},
            desc="Se rinde una sola vez. Las no respondidas cuentan 0. Devuelve nivel global, brechas y skills que cumple."),
        req("Último resultado", "GET", "/api/v1/nivelacion/resultado", espera=[200, 204]),
        req("Mis niveles por skill", "GET", "/api/v1/me/niveles-skill"),
    ]),
    ("14 · Tests de nivelación (admin)", "", [
        req("Crear test de nivelación", "POST", "/api/v1/admin/tests-nivelacion", ADMIN,
            body={"titulo": "Nivelación Postman {{$timestamp}}", "cargoId": "{{cargoId}}", "area": "backend", "preguntasIds": ["{{preguntaId}}", "{{preguntaId}}", "{{preguntaId}}"]},
            espera=[201, 400], omitir_en_prueba=True, guarda={"testNivelacionId": ("j.testId", lambda j: j.get("testId"))},
            desc="3 a 30 preguntas aprobadas, distintas y sin video (reemplaza los ids de ejemplo). `nivelObjetivo` por defecto `mixto`."),
        req("Listar tests", "GET", "/api/v1/admin/tests-nivelacion", ADMIN, consulta=[("activo", "true")]),
        req("Ver test", "GET", "/api/v1/admin/tests-nivelacion/{{testNivelacionId}}", ADMIN, omitir_en_prueba=True),
        req("Editar test", "PUT", "/api/v1/admin/tests-nivelacion/{{testNivelacionId}}", ADMIN,
            body={"titulo": "Nivelación Postman (editada)", "cargoId": "{{cargoId}}", "area": "backend", "nivelObjetivo": "mixto",
                  "preguntasIds": ["<id de pregunta 1>", "<id de pregunta 2>", "<id de pregunta 3>"]}, omitir_en_prueba=True),
        req("Desactivar test", "DELETE", "/api/v1/admin/tests-nivelacion/{{testNivelacionId}}", ADMIN, omitir_en_prueba=True,
            desc="Baja lógica. `GET` y `PUT /api/v1/admin/tests-nivelacion/{id}` para ver y editar."),
    ]),
    ("15 · App Android (/api/prueba-practica)", "Contrato que ya usa la app: crea y rinde cada prueba de una vez.", [
        req("Crear práctica (PR)", "POST", "/api/prueba-practica/front",
            body={"sector": "backend", "nivel": "jr", "metaCargo": "{{nombreCargo}}", "tipoPrueba": "PR"}, espera=[201, 409], guarda={
                "pruebaId": ("j.pruebaId", lambda j: j.get("pruebaId")),
                "preguntaAppId": ("j.preguntas[0].preguntaId", lambda j: (j.get("preguntas") or [{}])[0].get("preguntaId", "")),
                "opcionAppId": ("(j.preguntas[0].configRespuesta.opciones || [{id: ''}])[0].id",
                                lambda j: ((j.get("preguntas") or [{}])[0].get("configRespuesta", {}).get("opciones") or [{"id": ""}])[0]["id"])},
            desc="`tipoPrueba`: ENT entrevista · PR práctica técnica · BL práctica blanda · NV nivelación. Busca el cargo por `metaCargo`."),
        req("Enviar respuestas", "POST", "/api/prueba-practica/{{pruebaId}}/respuestas",
            body={"pruebaId": "{{pruebaId}}", "respuestas": [{"preguntaId": "{{preguntaAppId}}", "opcionesSeleccionadas": ["{{opcionAppId}}"], "respuestaTexto": "Mi respuesta"}]},
            espera=[200, 400, 404],
            desc="Acepta `respuestaTexto` (práctica, nivelación) y `respuestaAbierta` (entrevista). Las que vienen en blanco se omiten."),
        req("Historial de intentos", "GET", "/api/prueba-practica/intentos"),
    ]),
    ("16 · Administración de usuarios", "Solo admin.", [
        req("Listar usuarios", "GET", "/admin/usuarios", ADMIN),
        req("Crear usuario", "POST", "/admin/usuarios", ADMIN,
            body={"correo": "creado.{{$timestamp}}@ejemplo.com", "contrasena": "Clave-segura-1", "nombre": "Creado por admin", "rol": "user"}, espera=[201],
            desc="Alias antiguo: `POST /admin/users`."),
        req("Cambiar rol", "PATCH", "/admin/usuarios/{{usuarioId}}/rol", ADMIN, body={"nuevoRol": "user"}, omitir_en_prueba=True,
            desc="`user` | `admin`. Un admin no puede quitarse el rol a sí mismo."),
        req("Restablecer contraseña", "PATCH", "/admin/usuarios/{{usuarioId}}/password", ADMIN, body={"nuevaContrasena": "Clave-segura-1"}, omitir_en_prueba=True),
        req("Desactivar usuario", "DELETE", "/admin/usuarios/{{usuarioId}}", ADMIN, omitir_en_prueba=True,
            desc="⚠️ No borra: desactiva y cierra sus sesiones. `PATCH /admin/usuarios/{id}/activar` lo reactiva."),
        req("Reactivar usuario", "PATCH", "/admin/usuarios/{{usuarioId}}/activar", ADMIN, omitir_en_prueba=True),
    ]),
    ("17 · Cierre de sesión", "", [
        req("Cerrar sesión", "POST", "/auth/logout", PUBLICA, body={"refreshToken": "{{refreshToken}}"}, desc="Revoca el refresh token."),
    ]),
    ("18 · Rutas antiguas (alias)", "Rutas que siguen funcionando por compatibilidad con el panel y la app anteriores. "
     "Para código nuevo, usar las de las carpetas anteriores.", [
        req("Cargos (alias)", "GET", "/market/cargos", PUBLICA, desc="Igual que `GET /api/v1/cargos`."),
        req("Requisitos del cargo (formato antiguo)", "GET", "/market/cargos/{{cargoId}}/skills", PUBLICA,
            desc="Solo la lista de requisitos (la matriz completa está en `GET /api/v1/cargos/{id}/skills`)."),
        req("Crear usuario (alias)", "POST", "/admin/users", ADMIN,
            body={"correo": "alias.{{$timestamp}}@ejemplo.com", "contrasena": "Clave-segura-1", "rol": "user"}, espera=[201],
            desc="Igual que `POST /admin/usuarios`."),
        req("Publicar texto de consentimiento (alias)", "POST", "/admin/consent/text", ADMIN,
            body={"version": "2.0.0", "title": "EULA", "body": "Texto"}, omitir_en_prueba=True,
            desc="⚠️ Igual que `POST /api/v1/legal/admin/eula`: cambia el texto vigente para todos."),
        req("Generar preguntas con IA (alias)", "POST", "/api/v1/admin/questions/generate-ai", ADMIN,
            body={"cargo_id": "{{cargoId}}", "nivel": "junior", "cantidad": 2}, omitir_en_prueba=True,
            desc="⚠️ Igual que `POST /api/v1/admin/preguntas/generar-ia` (llama al LLM)."),
        req("Callback de Google (web)", "GET", "/auth/google/callback", PUBLICA, omitir_en_prueba=True,
            desc="Lo llama Google al terminar el login web; no se usa desde Postman."),
    ]),
]


# ─── Colección Postman v2.1 ──────────────────────────────────────────────────

def item_postman(r):
    ruta = r["ruta"].lstrip("/")
    url = {"raw": "{{baseUrl}}/" + ruta, "host": ["{{baseUrl}}"], "path": ruta.split("/")}
    if r["consulta"]:
        url["query"] = [{"key": k, "value": v} for k, v in r["consulta"]]
        url["raw"] += "?" + "&".join(f"{k}={v}" for k, v in r["consulta"])
    peticion = {"method": r["metodo"], "header": [], "url": url, "description": r["desc"]}
    if r["auth"] == PUBLICA:
        peticion["auth"] = {"type": "noauth"}
    elif r["auth"] == ADMIN:
        peticion["auth"] = {"type": "bearer", "bearer": [{"key": "token", "value": "{{adminToken}}", "type": "string"}]}
    if r["body"] is not None:
        peticion["header"].append({"key": "Content-Type", "value": "application/json"})
        peticion["body"] = {"mode": "raw", "raw": json.dumps(r["body"], ensure_ascii=False, indent=2), "options": {"raw": {"language": "json"}}}
    item = {"name": r["nombre"], "request": peticion}
    if r["guarda"]:
        lineas = ["if (pm.response.code >= 200 && pm.response.code < 300 && pm.response.text()) {", "    const j = pm.response.json();"]
        lineas += [f"    pm.collectionVariables.set(\"{var}\", {js});" for var, (js, _) in r["guarda"].items()]
        lineas.append("}")
        item["event"] = [{"listen": "test", "script": {"type": "text/javascript", "exec": lineas}}]
    return item


def coleccion():
    return {
        "info": {
            "_postman_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "entrevistaapp-api")),
            "name": NOMBRE,
            "description": "API del backend de EntrevistaAPP. Documentación completa: docs/documentacion/API.md (generada junto con esta colección).\n\n"
                           "1. Importar también el entorno `EntrevistaAPP_local.postman_environment.json` (o cambiar `baseUrl`).\n"
                           "2. Ejecutar **Registrar usuario** o **Login**: los tokens se guardan solos.\n"
                           "3. Para lo de admin, **Login admin** con una cuenta de rol admin.\n"
                           "Las carpetas están en el orden del flujo: cada request guarda los ids que usa la siguiente.\n"
                           "⚠️ = cuesta dinero, consume cuota de APIs externas, manda correos o borra datos.",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "auth": {"type": "bearer", "bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}]},
        "variable": [{"key": k, "value": v} for k, v in VARIABLES.items()],
        "item": [{"name": nombre, "description": desc, "item": [item_postman(r) for r in reqs]} for nombre, desc, reqs in CARPETAS],
    }


def entorno():
    return {
        "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "entrevistaapp-local")),
        "name": "EntrevistaAPP local",
        "values": [{"key": k, "value": VARIABLES[k], "enabled": True, "type": "secret" if "contrasena" in k.lower() else "default"}
                   for k in ("baseUrl", "correo", "contrasena", "correoAdmin", "contrasenaAdmin")],
        "_postman_variable_scope": "environment",
    }

# ─── API.md ──────────────────────────────────────────────────────────────────

INTRODUCCION = """# API de EntrevistaAPP

> Archivo generado por `docs/documentacion/GENERAR_DOCUMENTACION.py`. No editar a mano: cambiar la definición y volver a generar.

Backend Kotlin + Ktor. Todas las respuestas son JSON en UTF-8. La colección de Postman con los mismos endpoints está en
[`postman/`](postman/) (ver [README](README.md)).

## Conceptos generales

### URL base
| Entorno | URL |
|---|---|
| Local | `http://localhost:8080` |

### Autenticación
- **JWT Bearer**: `Authorization: Bearer <accessToken>`. El `accessToken` dura **15 minutos**.
- `POST /auth/login` y `POST /auth/register` entregan `accessToken` y `refreshToken` (dura **15 días**).
- `POST /auth/refresh` rota el refresh token: el anterior deja de servir. Reusar uno ya rotado revoca todas las sesiones.
- **Roles**: `user` y `admin`. Las rutas de admin responden `401` sin token y `403` con un token de usuario normal.
- En las tablas: **Pública** (sin token), **Usuario** (cualquier sesión), **Admin** (rol admin).

### Formato de error
Todos los errores usan el mismo cuerpo, con un código estable para el cliente y un mensaje en español para mostrar:
```json
{ "error": "entrevista_en_progreso", "mensaje": "Ya tienes una entrevista en curso…", "message": "Ya tienes una entrevista en curso…" }
```
`message` repite `mensaje` por compatibilidad con la app Android. Excepción: `POST /auth/register` responde
`{"error": "email_in_use"}` (409) y `{"error": "invalid_country" | "invalid_birthdate"}` (422), como lo espera la app.

| HTTP | Cuándo |
|---|---|
| 400 | Datos inválidos o regla de negocio no cumplida (`id_invalido`, `nivel_invalido`, `sin_respuestas`…) |
| 401 | Sin sesión, token vencido o credenciales incorrectas |
| 403 | Sesión válida sin permiso (rutas de admin) |
| 404 | No existe, o es de otro usuario (no se revela que existe) |
| 409 | Choca con el estado actual (`entrevista_en_progreso`, `pregunta_ya_respondida`, `compra_ya_registrada`…) |
| 429 | Límite superado: `demasiadas_solicitudes` (por IP) o `demasiados_intentos` (login) |
| 500 | Error inesperado (`error_interno`); el detalle queda en el log, nunca en la respuesta |
| 502 | Un proveedor externo respondió algo inutilizable (ej: el LLM) |
| 503 | Un proveedor externo o la base de datos no está disponible |

### Convenciones
- **Fechas**: ISO-8601 en UTC (`2026-10-03T14:05:00Z`); fechas sin hora `YYYY-MM-DD`.
- **Ids**: UUID. Un id mal formado en la ruta responde `400 id_invalido`.
- **Niveles**: `junior | semisenior | senior` (también se aceptan `jr | mid | sr`).
- **Paginación**: `?pagina=1&tamano=20` donde aplica (tamaño máximo 100).
- **Límites por IP**: registro 30 cada 10 min; recuperación de contraseña 5 cada 15 min. **Login**: 5 contraseñas
  incorrectas bloquean el correo 15 min.
- ⚠️ en una descripción = cuesta dinero (LLM), consume cuota de APIs externas, manda correos o borra datos.
"""

ETIQUETA_AUTH = {PUBLICA: "Pública", USUARIO: "Usuario", ADMIN: "Admin"}


def ancla(texto):
    texto = texto.lower()
    texto = re.sub(r"[^\w\s-]", "", texto, flags=re.UNICODE)
    # Igual que GitHub: cada espacio pasa a guion (no se colapsan).
    return texto.strip().replace(" ", "-")


def catalogo_errores():
    """Códigos de error que lanza el backend, leídos del código fuente (nunca quedan desactualizados)."""
    estado = {"ErrorValidacion": 400, "ErrorNoAutorizado": 401, "ErrorProhibido": 403, "ErrorNoEncontrado": 404,
              "ErrorConflicto": 409, "ErrorDemasiadosIntentos": 429, "ErrorRespuestaExterna": 502, "ErrorServicioExterno": 503}
    patron = re.compile(r'(' + "|".join(estado) + r')\(\s*"([a-z0-9_]+)"(?:\s*,\s*"((?:[^"\\]|\\.)*)")?')
    codigos = {}
    for raiz, _, archivos in os.walk(CODIGO_FUENTE):
        for nombre in archivos:
            if not nombre.endswith(".kt"):
                continue
            texto = open(os.path.join(raiz, nombre), encoding="utf-8").read()
            for clase, codigo, mensaje in patron.findall(texto):
                actual = codigos.get((estado[clase], codigo))
                if actual is None or (not actual and mensaje):
                    codigos[(estado[clase], codigo)] = mensaje
    filas = ["| HTTP | Código | Mensaje |", "|---|---|---|"]
    for (http, codigo), mensaje in sorted(codigos.items()):
        mensaje = re.sub(r"\$\{[^}]*\}|\$[A-Za-z_]+", "…", mensaje).replace("|", "\\|")
        filas.append(f"| {http} | `{codigo}` | {mensaje} |")
    return "\n".join(filas), len(codigos)


def api_md():
    partes = [INTRODUCCION, "## Índice\n"]
    partes += [f"- [{nombre}](#{ancla(nombre)})" for nombre, _, _ in CARPETAS]
    partes.append("- [Catálogo de códigos de error](#catálogo-de-códigos-de-error)\n")
    for nombre, desc, reqs in CARPETAS:
        partes.append(f"## {nombre}\n")
        if desc:
            partes.append(desc + "\n")
        partes += ["| Método | Ruta | Auth | Qué hace |", "|---|---|---|---|"]
        for r in reqs:
            partes.append(f"| `{r['metodo']}` | `{r['ruta']}` | {ETIQUETA_AUTH[r['auth']]} | {r['nombre']} |")
        partes.append("")
        for r in reqs:
            partes.append(f"### {r['nombre']}\n")
            ruta = r["ruta"] + ("?" + "&".join(f"{k}={v}" for k, v in r["consulta"]) if r["consulta"] else "")
            partes.append(f"`{r['metodo']} {ruta}` · {ETIQUETA_AUTH[r['auth']]} · responde " + ", ".join(f"`{e}`" for e in r["espera"]) + "\n")
            if r["desc"]:
                partes.append(r["desc"] + "\n")
            if r["body"] is not None:
                partes.append("```json\n" + json.dumps(r["body"], ensure_ascii=False, indent=2) + "\n```\n")
    tabla, cantidad = catalogo_errores()
    partes += ["## Catálogo de códigos de error\n",
               f"Los {cantidad} códigos que puede devolver el backend en `error`, leídos del código fuente.\n", tabla, ""]
    return "\n".join(partes)


# ─── --probar: recorre la colección contra un servidor ───────────────────────

def probar(url_base, correo, contrasena, correo_admin, contrasena_admin):
    variables = dict(VARIABLES, baseUrl=url_base, correo=correo, contrasena=contrasena, correoAdmin=correo_admin, contrasenaAdmin=contrasena_admin)

    def sustituir(texto):
        texto = texto.replace("{{$timestamp}}", str(int(time.time() * 1000)))
        for k, v in variables.items():
            texto = texto.replace("{{" + k + "}}", str(v or ""))
        return texto

    fallas, ejecutadas = [], 0
    for carpeta, _, reqs in CARPETAS:
        for r in reqs:
            if r["omitir"]:
                continue
            ruta = sustituir(r["ruta"]) + ("?" + "&".join(f"{k}={sustituir(v)}" for k, v in r["consulta"]) if r["consulta"] else "")
            datos = sustituir(json.dumps(r["body"], ensure_ascii=False)).encode() if r["body"] is not None else None
            cabeceras = {"Content-Type": "application/json"} if datos else {}
            token = {USUARIO: variables["accessToken"], ADMIN: variables["adminToken"]}.get(r["auth"])
            if token:
                cabeceras["Authorization"] = "Bearer " + token
            peticion = urllib.request.Request(url_base + ruta, data=datos, headers=cabeceras, method=r["metodo"])
            try:
                with urllib.request.urlopen(peticion, timeout=30) as respuesta:
                    estado, texto = respuesta.status, respuesta.read().decode()
            except urllib.error.HTTPError as e:
                estado, texto = e.code, e.read().decode()
            ejecutadas += 1
            ok = estado in r["espera"]
            print(f"  [{'OK' if ok else 'FALLA'}] {estado} {r['metodo']:6} {r['ruta']:55} {carpeta} · {r['nombre']}")
            if not ok:
                fallas.append(f"{r['nombre']}: {estado} {texto[:200]}")
            if 200 <= estado < 300 and r["guarda"] and texto:
                j = json.loads(texto)
                for var, (_, extraer) in r["guarda"].items():
                    try:
                        variables[var] = extraer(j) or ""
                    except (KeyError, IndexError, TypeError):
                        variables[var] = ""
    print(f"\nResultado: {ejecutadas - len(fallas)} de {ejecutadas} requests respondieron lo esperado")
    for f in fallas:
        print("  -", f)
    return not fallas


if __name__ == "__main__":
    os.makedirs(CARPETA_POSTMAN, exist_ok=True)
    with open(os.path.join(CARPETA_POSTMAN, "EntrevistaAPP.postman_collection.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(coleccion(), f, ensure_ascii=False, indent=2)
    with open(os.path.join(CARPETA_POSTMAN, "EntrevistaAPP_local.postman_environment.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(entorno(), f, ensure_ascii=False, indent=2)
    with open(os.path.join(CARPETA, "API.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write(api_md())
    total = sum(len(r) for _, _, r in CARPETAS)
    print(f"Generado: API.md y colección de Postman ({len(CARPETAS)} carpetas, {total} requests)")
    if "--probar" in sys.argv:
        argumentos = sys.argv[sys.argv.index("--probar") + 1:]
        sys.exit(0 if probar(*argumentos) else 1)
