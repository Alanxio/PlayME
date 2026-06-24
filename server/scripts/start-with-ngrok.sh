#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$SERVER_DIR/.." && pwd)"
SETTINGS_FILE="$PROJECT_DIR/src/playme/Settings.java"
NGROK_URL_FILE="$SERVER_DIR/.ngrok-url"
NGROK_API="http://127.0.0.1:4040/api/tunnels"
APP_PORT="${PORT:-4000}"

NODE_PID=""
NGROK_PID=""

cd "$SERVER_DIR"

# Cargar variables de entorno
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

# ── Cleanup ──────────────────────────────────────────────────────────────────
cleanup() {
  echo ""
  echo "[start-with-ngrok] Cerrando procesos..."
  if [ -n "$NODE_PID" ] && kill -0 "$NODE_PID" >/dev/null 2>&1; then
    kill "$NODE_PID" >/dev/null 2>&1 || true
    wait "$NODE_PID" 2>/dev/null || true
  fi
  if [ -n "$NGROK_PID" ] && kill -0 "$NGROK_PID" >/dev/null 2>&1; then
    kill "$NGROK_PID" >/dev/null 2>&1 || true
    wait "$NGROK_PID" 2>/dev/null || true
  fi
  rm -f "$NGROK_URL_FILE"
}

trap cleanup EXIT INT TERM

# ── Validar ngrok ────────────────────────────────────────────────────────────
NGROK_BIN=""
for candidate in /snap/bin/ngrok "$PROJECT_DIR/bin/ngrok" ngrok; do
  if command -v "$candidate" >/dev/null 2>&1 || [ -x "$candidate" ]; then
    NGROK_BIN="$candidate"
    break
  fi
done

if [ -z "$NGROK_BIN" ]; then
  echo "[start-with-ngrok] ERROR: No se encontró el binario de ngrok." >&2
  echo "Instálalo con: sudo snap install ngrok" >&2
  exit 1
fi

if [ -z "${NGROK_AUTHTOKEN:-}" ]; then
  echo "[start-with-ngrok] ERROR: Falta NGROK_AUTHTOKEN en server/.env" >&2
  exit 1
fi

# ── Iniciar servidor Node.js ─────────────────────────────────────────────────
echo "[start-with-ngrok] Iniciando servidor Node.js en puerto $APP_PORT..."
node src/app.js &
NODE_PID=$!

for i in $(seq 1 30); do
  if (echo >/dev/tcp/127.0.0.1/"$APP_PORT") >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! (echo >/dev/tcp/127.0.0.1/"$APP_PORT") >/dev/null 2>&1; then
  echo "[start-with-ngrok] ERROR: El servidor no respondió en el puerto $APP_PORT." >&2
  exit 1
fi

echo "[start-with-ngrok] Servidor listo en http://localhost:$APP_PORT"

# ── Iniciar ngrok ────────────────────────────────────────────────────────────
echo "[start-with-ngrok] Iniciando ngrok..."
NGROK_ARGS=(http "$APP_PORT" --authtoken "$NGROK_AUTHTOKEN" --log stdout)

if [ -n "${NGROK_HTTP_URL:-}" ]; then
  NGROK_ARGS+=(--url "$NGROK_HTTP_URL")
fi

"$NGROK_BIN" "${NGROK_ARGS[@]}" &
NGROK_PID=$!

# Esperar a que ngrok abra su API local
for i in $(seq 1 30); do
  if curl -s "$NGROK_API" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

# Obtener URL pública
TUNNEL_URL=$(curl -s "$NGROK_API" | grep -oP '"public_url"\s*:\s*"\K[^"]+' | head -1 || true)

if [ -z "$TUNNEL_URL" ] && [ -n "${NGROK_HTTP_URL:-}" ]; then
  TUNNEL_URL="$NGROK_HTTP_URL"
fi

# Limpiar espacios accidentales al inicio/final
TUNNEL_URL=$(echo "$TUNNEL_URL" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

if [ -z "$TUNNEL_URL" ]; then
  echo "[start-with-ngrok] ERROR: No se pudo obtener la URL de ngrok." >&2
  exit 1
fi

echo "$TUNNEL_URL" > "$NGROK_URL_FILE"

# ── Actualizar Settings.java ─────────────────────────────────────────────────
if [ -f "$SETTINGS_FILE" ]; then
  # Actualizar solo la linea de serverUrl sin tocar otras cadenas
  sed -i "s|public String serverUrl = \"[^\"]*\";|public String serverUrl = \"$TUNNEL_URL\";|" "$SETTINGS_FILE"
  echo "[start-with-ngrok] Settings.java actualizado: $TUNNEL_URL"
else
  echo "[start-with-ngrok] WARNING: No se encontró $SETTINGS_FILE" >&2
fi

# ── Resumen ──────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════"
echo "  PlayME MediaServer expuesto"
echo "═══════════════════════════════════════════"
echo "  Local:    http://localhost:$APP_PORT"
echo "  ngrok:    $TUNNEL_URL"
echo "  Settings: $SETTINGS_FILE"
echo "═══════════════════════════════════════════"
echo ""
echo "Recuerda hacer Clean & Build en NetBeans antes de ejecutar la app."
echo "Pulsa Ctrl+C para detener el servidor y ngrok."
echo ""

# Mantener vivo
wait "$NGROK_PID" 2>/dev/null || true
