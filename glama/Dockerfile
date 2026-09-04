# syntax=docker/dockerfile:1
# CrossPaste MCP server over stdio, for Glama's introspection checks.
#
# CrossPaste is a desktop clipboard manager; its MCP server is built into the
# app and speaks SSE on 127.0.0.1:13130. This image runs the app's headless
# daemon (no display needed) and bridges its SSE endpoint to stdio with
# mcp-remote, so the container behaves like a plain stdio MCP server.
# Self-contained: no build context files are needed.

FROM node:22-bookworm-slim

ARG CROSSPASTE_VERSION=2.2.0
ARG CROSSPASTE_BUILD=2544
ARG TARGETARCH

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl \
      libfreetype6 fontconfig \
 && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    case "${TARGETARCH}" in \
      amd64) arch=amd64 ;; \
      arm64) arch=aarch64 ;; \
      *) echo "unsupported arch ${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    curl -fsSL -o /tmp/crosspaste.tar.gz \
      "https://github.com/CrossPaste/crosspaste-desktop/releases/download/${CROSSPASTE_VERSION}.${CROSSPASTE_BUILD}/crosspaste-${CROSSPASTE_VERSION}-${CROSSPASTE_BUILD}-linux-${arch}.tar.gz"; \
    mkdir -p /opt/crosspaste; \
    tar -xzf /tmp/crosspaste.tar.gz -C /opt/crosspaste --strip-components=1; \
    rm /tmp/crosspaste.tar.gz

RUN npm install -g mcp-remote@0.8.3

RUN useradd -m -u 1987 mcp

# Start the headless daemon with the MCP server enabled, wait for its SSE
# endpoint, then bridge it to stdio. Daemon output goes to stderr so stdout
# stays clean for the MCP protocol.
COPY <<'EOF' /usr/local/bin/entrypoint.sh
#!/bin/sh
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
exec mcp-remote "http://127.0.0.1:${PORT}/" --transport sse-only --allow-http
EOF
RUN chmod 755 /usr/local/bin/entrypoint.sh

USER mcp
WORKDIR /home/mcp
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
