#!/bin/sh
# D49: con TS_AUTHKEY definida, levanta Tailscale en userspace (SOCKS5 en localhost:1055) y exporta
# MOTOR_SOCKS_PROXY solo si quedó conectado; sin la variable, el backend arranca directo como antes.
# TS_AUTHKEY y WOL_TOKEN nunca se imprimen en estos logs.
set -eu
if [ -n "${TS_AUTHKEY:-}" ]; then
  echo "[start] iniciando tailscaled (userspace, socks5 localhost:1055)"
  tailscaled --tun=userspace-networking --socks5-server=localhost:1055 --state=mem: >/tmp/tailscaled.log 2>&1 &
  tailscale up --authkey="$TS_AUTHKEY" --hostname="${TS_HOSTNAME:-railway-backend}" --accept-dns=false >/tmp/tailscale-up.log 2>&1 || true
  intento=0
  while [ "$intento" -lt 30 ]; do
    if tailscale ip -4 >/dev/null 2>&1; then break; fi
    intento=$((intento + 1))
    sleep 1
  done
  if tailscale ip -4 >/dev/null 2>&1; then
    echo "[start] tailscale conectado: railway-backend ($(tailscale ip -4))"
    export MOTOR_SOCKS_PROXY="socks5://localhost:1055"
  else
    echo "[start] AVISO: tailscale NO conecto (revisa TS_AUTHKEY); motor y WOL quedan inalcanzables"
  fi
fi
exec /app/install/bin/ktor-sample
