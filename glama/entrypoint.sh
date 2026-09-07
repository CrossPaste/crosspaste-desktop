#!/bin/sh
# Starts the CrossPaste headless daemon with the MCP server enabled, waits for
# its SSE endpoint, then bridges it to stdio with supergateway. Used by
# glama/Dockerfile and by the Glama-hosted build (CMD ["sh", "glama/entrypoint.sh"]).
# Daemon output goes to stderr so stdout stays clean for the MCP protocol.
set -eu

PORT=13130
DATA_DIR="$HOME/.local/share/.crosspaste"

mkdir -p "$DATA_DIR"
if [ ! -f "$DATA_DIR/appConfig.json" ]; then
  printf '{"language":"en","enableMcpServer":true,"mcpServerPort":%s}\n' "$PORT" > "$DATA_DIR/appConfig.json"
fi

/opt/crosspaste/bin/crosspaste --headless 1>&2 &
DAEMON_PID=$!

# The SSE endpoint never closes, so a timed-out request that already got a
# 200 counts as ready.
i=0
until [ "$(curl -s -m 2 -o /dev/null -w '%{http_code}' -H 'Accept: text/event-stream' "http://127.0.0.1:${PORT}/" 2>/dev/null)" = "200" ]; do
  i=$((i + 1))
  if [ "$i" -ge 120 ]; then
    echo "crosspaste MCP endpoint did not come up on port ${PORT}" >&2
    kill "$DAEMON_PID" 2>/dev/null || true
    exit 1
  fi
  if ! kill -0 "$DAEMON_PID" 2>/dev/null; then
    echo "crosspaste daemon exited during startup" >&2
    exit 1
  fi
  sleep 1
done

trap 'kill "$DAEMON_PID" 2>/dev/null || true' EXIT INT TERM
exec supergateway --sse "http://127.0.0.1:${PORT}/"
