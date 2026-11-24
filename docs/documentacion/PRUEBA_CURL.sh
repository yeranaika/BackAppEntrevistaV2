#!/usr/bin/env bash
# Prueba rápida con curl de los endpoints seguros (sin LLM, sin APIs de empleo, sin correos).
# Crea un usuario temporal y lo borra al final.
#
# Uso (Git Bash):  bash docs/documentacion/PRUEBA_CURL.sh [URL_BASE]
# Por defecto:     http://localhost:8095

BASE="${1:-http://localhost:8095}"
EMAIL="curl.$(date +%s)@ejemplo.com"
PASS="Clave-segura-1"
OK=0; FALLA=0

# uso: probar "nombre" CODIGO_ESPERADO METODO RUTA [body] [token]
probar() {
  local nombre="$1" esperado="$2" metodo="$3" ruta="$4" body="$5" token="$6"
  local args=(-s -o /tmp/curl_resp.txt -w "%{http_code}" -X "$metodo" "$BASE$ruta")
  [ -n "$body" ]  && args+=(-H "Content-Type: application/json" -d "$body")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  local codigo; codigo=$(curl "${args[@]}")
  if [[ " $esperado " == *" $codigo "* ]]; then OK=$((OK+1)); echo "[OK]    $codigo $metodo $ruta  ($nombre)"
  else FALLA=$((FALLA+1)); echo "[FALLA] $codigo (esperado $esperado) $metodo $ruta  ($nombre)"; head -c 300 /tmp/curl_resp.txt; echo; fi
}
campo() { tr -d '\n' < /tmp/curl_resp.txt | sed -n "s/.*\"$1\" *: *\"\([^\"]*\)\".*/\1/p"; }

echo "== Servidor: $BASE   usuario: $EMAIL"

echo; echo "-- Públicos"
probar "Salud"                 200 GET /health
probar "Documento EULA"        200 GET /api/v1/legal/eula
probar "Versiones legales"     200 GET /api/v1/legal/versions
probar "Consentimiento actual" 200 GET /consent/current
probar "Cargos"                200 GET /api/v1/cargos
probar "Skills en tendencia"   200 GET /api/v1/skills/trending
probar "Skills de mercado"     200 GET /market/skills

echo; echo "-- Autenticación"
probar "Registrar" 201 POST /auth/register \
  "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"nombre\":\"Usuario Curl\",\"nivelExperiencia\":\"junior\",\"area\":\"TI\"}"
probar "Registrar repetido (409)" 409 POST /auth/register \
  "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"nombre\":\"Usuario Curl\",\"nivelExperiencia\":\"junior\",\"area\":\"TI\"}"
probar "Login clave mala (401)" 401 POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"incorrecta-1\"}"
probar "Login" 200 POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}"
TOKEN=$(campo accessToken); REFRESH=$(campo refreshToken)
[ -z "$TOKEN" ] && { echo "No se obtuvo accessToken; abortando."; exit 1; }
probar "Refresh" 200 POST /auth/refresh "{\"refreshToken\":\"$REFRESH\"}"
TOKEN=$(campo accessToken); REFRESH=$(campo refreshToken)

echo; echo "-- Usuario (con token)"
probar "Sin token (401)"        401 GET /me
probar "Ruta admin (403)"       403 GET /admin/usuarios "" "$TOKEN"
probar "Mi cuenta"              200 GET /me "" "$TOKEN"
probar "Actualizar cuenta"      200 PUT /me '{"nombre":"Usuario Curl","idioma":"es"}' "$TOKEN"
probar "Mi perfil"              "200 404" GET /me/perfil "" "$TOKEN"
probar "Estado onboarding"      200 GET /onboarding/status "" "$TOKEN"
probar "Objetivo"               "200 204 404" GET /me/objetivo "" "$TOKEN"
probar "Estado de suscripción"  200 GET /billing/status "" "$TOKEN"
probar "Preferencias recordat." "200 404" GET /recordatorios/preferencias "" "$TOKEN"
probar "Último consentimiento"  "200 404" GET /me/consent/latest "" "$TOKEN"
probar "Entrevista en curso"    "200 204" GET /api/v1/entrevistas/actual "" "$TOKEN"
probar "Historial entrevistas"  200 GET /api/v1/entrevistas "" "$TOKEN"
probar "Reportes"               200 GET /api/v1/reportes "" "$TOKEN"
probar "Mi progreso"            200 GET /api/v1/me/progreso "" "$TOKEN"
probar "Historial prácticas"    200 GET /api/v1/practicas "" "$TOKEN"
probar "Historial pruebas"      200 GET /api/v1/pruebas/historial "" "$TOKEN"
probar "Niveles por skill"      200 GET /api/v1/me/niveles-skill "" "$TOKEN"
probar "Intentos app Android"   200 GET /api/prueba-practica/intentos "" "$TOKEN"
probar "Id mal formado (400)"   400 GET /api/v1/entrevistas/no-es-uuid "" "$TOKEN"

echo; echo "-- Cierre y limpieza"
probar "Logout"                 200 POST /auth/logout "{\"refreshToken\":\"$REFRESH\"}"
probar "Login para borrar"      200 POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}"
TOKEN=$(campo accessToken)
probar "Eliminar cuenta de prueba" 200 DELETE /cuenta '{"confirmar":"eliminar"}' "$TOKEN"

echo; echo "Resultado: $OK correctas, $FALLA con falla"
[ "$FALLA" -eq 0 ]
