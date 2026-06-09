#!/usr/bin/env bash
# Build a NON-PRODUCTION Windows portable zip for testing the in-app self-updater
# locally, without publishing a release.
#
# It mirrors the CI "Prepare Dependency" + Conveyor steps, but:
#   * uses appEnv=BETA  -> a packaged build with a real path provider that still
#     honors a *loopback* CROSSPASTE_UPDATE_BASE_URL (serve on the same machine).
#     NOTE: do NOT use appEnv=TEST for a packaged build — TEST makes
#     DesktopAppPathProvider return `this` (meant only for mocked unit tests),
#     which infinite-recurses in resolve() and crashes on startup.
#   * builds only the `windows-zip` task instead of the full `make site`.
#
# Conveyor cross-builds the Windows zip from any host (macOS / Linux / Windows).
#
# Requirements: Conveyor CLI (https://conveyor.hydraulic.dev), Node, the project's
# Gradle/JDK. The Windows JBR tarball under app/jbr/ is fetched by a prior
# `./gradlew app:build` (or any packaging build) if not already present.
#
# Usage: scripts/build-test-windows-zip.sh [appEnv] [outputDir]
#   appEnv     default BETA  (use BETA; see the TEST warning above)
#   outputDir  default output-test
#
# See doc/en/WindowsZipSelfUpdateTest.md for the full test runbook.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APP_ENV="${1:-BETA}"
OUT_DIR="${2:-output-test}"

command -v conveyor >/dev/null 2>&1 || {
    echo "ERROR: conveyor CLI not found. Install from https://conveyor.hydraulic.dev" >&2
    exit 1
}
command -v node >/dev/null 2>&1 || { echo "ERROR: node not found" >&2; exit 1; }

if [ "$APP_ENV" = "TEST" ]; then
    echo "ERROR: appEnv=TEST crashes a packaged build (path provider recurses). Use BETA." >&2
    exit 1
fi
if [ "$APP_ENV" = "PRODUCTION" ]; then
    echo "WARNING: a PRODUCTION build only honors a loopback CROSSPASTE_UPDATE_BASE_URL;" >&2
    echo "         BETA behaves the same here and is the intended test env." >&2
fi

echo "==> writeConveyorConfig (appEnv=$APP_ENV)"
./gradlew -q writeConveyorConfig -PappEnv="$APP_ENV"
mv app/generated.conveyor.conf generated.conveyor.conf

echo "==> rewrite conveyor config (move Windows native libs, drop cross-platform jars)"
node ./.github/scripts/rewriteConveyorConf.js generated.conveyor.conf
node ./.github/scripts/processJarFiles.js "tesseract-.*-macosx-arm64.jar" generated.conveyor.conf
node ./.github/scripts/processJarFiles.js "tesseract-.*-macosx-x86_64.jar" generated.conveyor.conf
node ./.github/scripts/checkDuplicateJars.js generated.conveyor.conf

echo "==> conveyor make windows-zip -> $OUT_DIR/"
rm -rf "$OUT_DIR"
conveyor make windows-zip --output-dir "$OUT_DIR"

ZIP="$(ls "$OUT_DIR"/*.zip 2>/dev/null | head -1 || true)"
echo
echo "Built: ${ZIP:-<no zip found in $OUT_DIR>}"
echo "Next: serve it as a 'newer' release, e.g."
echo "  scripts/serve-fake-update.sh \"$ZIP\" 2.1.5 9999"
