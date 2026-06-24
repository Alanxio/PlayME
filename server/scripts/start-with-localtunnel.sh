#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$SERVER_DIR/.." && pwd)"
SETTINGS_FILE="$PROJECT_DIR/src/playme/Settings.java"
APP_PORT="${PORT:-4000}"
SUBDOMAIN="${LOCALTUNNEL_SUBDOMAIN:-playmenokia}"

NODE_PID=""
LT_PID=""

cd "$SERVER_DIR"

# ── Cleanup ──────────────────────────────────────────────────────────────────
cleanup() {
  echo ""
  echo "[start-with-localtunnel] Cerrando procesos..."
  if [ -n "$LT_PID" ] && kill -0 "$LT_PID" >/dev/null 2>&1; then
    kill "$LT_PID" >/dev/null 2>&1 || true
    wait "$LT_PID" 2>/dev/null || true
  fi
  if [ -n "$NODE_PID" ] && kill -0 "$NODE_PID" >/dev/null 2>&1; then
    kill "$NODE_PID" >/dev/null 2>&1 || true
    wait "$NODE_PID" 2>/dev/null || true
  fi
}

trap cleanup EXIT INT TERM

# ── Validar localtunnel ──────────────────────────────────────────────────────
LT_BIN=""
for candidate in ./node_modules/.bin/lt npx; do
  if command -v "$candidate" >/dev/null 2>&1 || [ -x "$candidate" ]; then
    LT_BIN="$candidate"
    break
  fi
done

if [ -z "$LT_BIN" ]; then
  echo "[start-with-localtunnel] ERROR: No se encontró localtunnel." >&2
  echo "Instálalo con: cd $SERVER_DIR && npm install localtunnel" >&2
  exit 1
fi

# ── Iniciar servidor Node.js ─────────────────────────────────────────────────
echo "[start-with-localtunnel] Iniciando servidor Node.js en puerto $APP_PORT..."
node src/app.js &
NODE_PID=$!

for i in $(seq 1 30); do
  if (echo >/dev/tcp/127.0.0.1/"$APP_PORT") >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! (echo >/dev/tcp/127.0.0.1/"$APP_PORT") >/dev/null 2>&1; then
  echo "[start-with-localtunnel] ERROR: El servidor no respondió en el puerto $APP_PORT." >&2
  exit 1
fi

echo "[start-with-localtunnel] Servidor listo en http://localhost:$APP_PORT"

# ── Iniciar localtunnel ──────────────────────────────────────────────────────
TUNNEL_URL="https://$SUBDOMAIN.loca.lt"
HTTP_URL="http://$SUBDOMAIN.loca.lt"
echo "[start-with-localtunnel] Iniciando túnel con subdominio '$SUBDOMAIN'..."

if [ "$LT_BIN" = "npx" ]; then
  npx localtunnel --port "$APP_PORT" --subdomain "$SUBDOMAIN" >/tmp/localtunnel.log 2>&1 &
else
  "$LT_BIN" --port "$APP_PORT" --subdomain "$SUBDOMAIN" >/tmp/localtunnel.log 2>&1 &
fi
LT_PID=$!

# Esperar a que el túnel responda
for i in $(seq 1 30); do
  if curl -s -o /dev/null -w "%{http_code}" "$HTTP_URL/catalog/count" | grep -qE '200|401|403'; then
    break
  fi
  sleep 1
done

if ! curl -s -o /dev/null -w "%{http_code}" "$HTTP_URL/catalog/count" | grep -qE '200|401|403'; then
  echo "[start-with-localtunnel] ERROR: El túnel no respondió. Revisa /tmp/localtunnel.log" >&2
  exit 1
fi

# ── Actualizar Settings.java ─────────────────────────────────────────────────
if [ -f "$SETTINGS_FILE" ]; then
  sed -i 's|public String serverUrl *= *" *|public String serverUrl = "|; s| *";|;|' "$SETTINGS_FILE"
  sed -i "s|public String serverUrl = \"[^\"]*\";|public String serverUrl = \"$HTTP_URL\";|" "$SETTINGS_FILE"
  echo "[start-with-localtunnel] Settings.java actualizado: $HTTP_URL"
else
  echo "[start-with-localtunnel] WARNING: No se encontró $SETTINGS_FILE" >&2
fi

# ── Resumen ──────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════"
echo "  PlayME MediaServer expuesto"
echo "═══════════════════════════════════════════"
echo "  Local:     http://localhost:$APP_PORT"
echo "  HTTP:      $HTTP_URL"
echo "  HTTPS:     $TUNNEL_URL"
echo "  Settings:  $SETTINGS_FILE"
echo "═══════════════════════════════════════════"
echo ""
echo "Recuerda hacer Clean & Build en NetBeans antes de ejecutar la app."
echo "Pulsa Ctrl+C para detener el servidor y localtunnel."
echo ""

# Mantener vivo
wait "$LT_PID" 2>/dev/null || true
