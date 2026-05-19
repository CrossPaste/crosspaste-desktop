#!/usr/bin/env bash
# Boot the CrossPaste desktop app for a short time and fail if it crashes.
#
# Catches dependency / binary-compat regressions that `./gradlew app:build`
# can't see, because `build` never triggers the first Compose composition.
# Historic incidents this would have caught:
#   * Jewel 0.37 (compiled with Java 25) → UnsupportedClassVersionError on JBR 21
#   * compose-shimmer 1.4.0 vs Compose 1.11 → NoSuchMethodError on SidePasteItemView
#
# Usage: ./smoke-test.sh [boot_timeout_seconds] [compose_wait_seconds]
#   boot_timeout_seconds  max time to wait for the "CrossPaste started" log line (default 120)
#   compose_wait_seconds  extra time after boot to let the first Composition render (default 25)
#
# Exit code: 0 on success, 1 on detected failure, 2 on environment problem.

set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

BOOT_TIMEOUT="${1:-120}"
COMPOSE_WAIT="${2:-25}"
LOG_DIR="$ROOT/build/smoke-logs"
LOG_FILE="$LOG_DIR/app.log"

mkdir -p "$LOG_DIR"
: > "$LOG_FILE"

case "$(uname -s 2>/dev/null || echo Unknown)" in
  Linux*)  OS=linux ;;
  Darwin*) OS=mac ;;
  MINGW*|MSYS*|CYGWIN*) OS=windows ;;
  *) OS=unknown ;;
esac

GRADLE="./gradlew"
[ "$OS" = "windows" ] && GRADLE="./gradlew.bat"

# Jewel's DecoratedWindow requires JetBrainsRuntime (JBR). `app:run` would
# otherwise use whatever JVM Gradle was launched with (CI gives us Temurin),
# and the very first Composition would die with IllegalStateException. The
# `app:build` step has already downloaded the platform JBR tarball into
# app/jbr/ for nativeDistributions packaging — we just need to extract it and
# point JAVA_HOME at it before launching.
ARCH="$(uname -m 2>/dev/null || echo unknown)"
case "$OS:$ARCH" in
  linux:x86_64|linux:amd64) JBR_GLOB="jbrsdk-*-linux-x64-*.tar.gz" ;;
  linux:aarch64|linux:arm64) JBR_GLOB="jbrsdk-*-linux-aarch64-*.tar.gz" ;;
  mac:arm64) JBR_GLOB="jbrsdk-*-osx-aarch64-*.tar.gz" ;;
  mac:x86_64) JBR_GLOB="jbrsdk-*-osx-x64-*.tar.gz" ;;
  windows:*) JBR_GLOB="jbrsdk-*-windows-x64-*.tar.gz" ;;
  *)
    echo "[smoke] ERROR: no JBR mapping for os=$OS arch=$ARCH" >&2
    exit 2
    ;;
esac

JBR_DIR="$ROOT/app/jbr"
# shellcheck disable=SC2086
JBR_TARBALL="$(ls -1 $JBR_DIR/$JBR_GLOB 2>/dev/null | head -n1)"
if [ -z "${JBR_TARBALL:-}" ]; then
  echo "[smoke] ERROR: no JBR tarball matching '$JBR_GLOB' under $JBR_DIR." >&2
  echo "[smoke]        Run './gradlew app:build' first to populate it." >&2
  exit 2
fi

JBR_EXTRACT_DIR="$JBR_DIR/extracted"
JBR_MARKER="$JBR_EXTRACT_DIR/$(basename "$JBR_TARBALL").extracted"
if [ ! -f "$JBR_MARKER" ]; then
  echo "[smoke] extracting $(basename "$JBR_TARBALL") -> $JBR_EXTRACT_DIR"
  rm -rf "$JBR_EXTRACT_DIR"
  mkdir -p "$JBR_EXTRACT_DIR"
  tar -xzf "$JBR_TARBALL" -C "$JBR_EXTRACT_DIR"
  touch "$JBR_MARKER"
fi

JAVA_BIN="$(find "$JBR_EXTRACT_DIR" -maxdepth 5 -type f \( -name java -o -name java.exe \) -path '*/bin/*' 2>/dev/null | head -n1)"
if [ -z "$JAVA_BIN" ]; then
  echo "[smoke] ERROR: extracted JBR has no bin/java under $JBR_EXTRACT_DIR" >&2
  exit 2
fi
JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "[smoke] JAVA_HOME=$JAVA_HOME"
"$JAVA_BIN" -version 2>&1 | sed 's/^/[smoke] java: /'

LAUNCHER=()
if [ "$OS" = "linux" ]; then
  if [ -z "${DISPLAY:-}" ]; then
    if ! command -v xvfb-run >/dev/null 2>&1; then
      echo "[smoke] ERROR: no DISPLAY and xvfb-run not found." >&2
      echo "[smoke] Install with: sudo apt-get install -y xvfb" >&2
      exit 2
    fi
    LAUNCHER=(xvfb-run --auto-servernum --server-args="-screen 0 1280x720x24")
  fi
fi

echo "[smoke] os=$OS boot_timeout=${BOOT_TIMEOUT}s compose_wait=${COMPOSE_WAIT}s"
echo "[smoke] launching: ${LAUNCHER[*]} $GRADLE --no-daemon app:run"
echo "[smoke] log file: $LOG_FILE"

"${LAUNCHER[@]}" "$GRADLE" --no-daemon app:run >>"$LOG_FILE" 2>&1 &
APP_PID=$!
echo "[smoke] launched, pid=$APP_PID"

started=0
elapsed=0
while [ "$elapsed" -lt "$BOOT_TIMEOUT" ]; do
  if grep -q "CrossPaste started" "$LOG_FILE" 2>/dev/null; then
    started=1
    echo "[smoke] boot marker detected at ${elapsed}s"
    break
  fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "[smoke] launcher process died before boot marker (after ${elapsed}s)"
    break
  fi
  sleep 1
  elapsed=$((elapsed + 1))
done

if [ "$started" = "1" ]; then
  echo "[smoke] giving Compose ${COMPOSE_WAIT}s to render first frames"
  sleep "$COMPOSE_WAIT"
fi

# Tear down: try graceful, then force.
if kill -0 "$APP_PID" 2>/dev/null; then
  echo "[smoke] sending SIGTERM to launcher tree (pid=$APP_PID)"
  pkill -TERM -P "$APP_PID" 2>/dev/null || true
  kill -TERM "$APP_PID" 2>/dev/null || true
  for _ in $(seq 1 10); do
    kill -0 "$APP_PID" 2>/dev/null || break
    sleep 1
  done
fi
pkill -KILL -P "$APP_PID" 2>/dev/null || true
kill -KILL "$APP_PID" 2>/dev/null || true
# Best-effort: clean any orphan JVM the Gradle wrapper may have spawned.
pkill -KILL -f 'com\.crosspaste\.CrossPaste' 2>/dev/null || true
wait "$APP_PID" 2>/dev/null || true

echo "----- smoke app log tail -----"
tail -n 400 "$LOG_FILE" || true
echo "----- end smoke app log tail -----"

failures=0

if [ "$started" != "1" ]; then
  if grep -q "Another instance of the application is already running" "$LOG_FILE" 2>/dev/null; then
    echo "[smoke] FAIL: another CrossPaste DEV instance is already running and holds app.lock." >&2
    echo "[smoke]       Quit it (or 'pkill -f com.crosspaste.CrossPaste') and rerun." >&2
  else
    echo "[smoke] FAIL: app never logged 'CrossPaste started' within ${BOOT_TIMEOUT}s"
  fi
  failures=$((failures + 1))
fi

# Fatal markers: real uncaught throwables + classic class-loading / binary-compat errors.
# Keep narrow on purpose — broad "Error|Exception" matches noisy debug logs and library names.
fatal_re='^Exception in thread |^Caused by: |Uncaught exception in thread|UnsupportedClassVersionError|NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException|IncompatibleClassChangeError|AbstractMethodError|LinkageError'

if grep -Eq "$fatal_re" "$LOG_FILE"; then
  echo "[smoke] FAIL: fatal pattern detected in app log:"
  grep -nE "$fatal_re" "$LOG_FILE" | head -50
  failures=$((failures + 1))
fi

if [ "$failures" -gt 0 ]; then
  echo "[smoke] smoke test FAILED (full log at $LOG_FILE)"
  exit 1
fi

echo "[smoke] smoke test PASSED"
exit 0
