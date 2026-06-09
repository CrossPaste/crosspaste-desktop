#!/usr/bin/env bash
# Build the four files needed to test the Windows portable-zip self-updater from a
# remote OSS test bucket (https://oss.crosspaste.com/test/), with zero local server:
#
#   1. crosspaste-<OLD>-windows-amd64.zip            the "old" app you install & run
#   2. crosspaste-<NEW>-<REV>-windows-amd64.zip      the "new" build the updater pulls
#   3. checksum.txt                                  sha256 of (2), in shasum format
#   4. metadata.properties                           advertises <NEW> / <REV>
#
# Upload all four to the OSS test bucket, then on the Windows machine:
#   set CROSSPASTE_UPDATE_BASE_URL=https://oss.crosspaste.com/test
#   "<extracted-old-zip>\bin\CrossPaste.exe"
# The BETA build honors the remote override (only PRODUCTION is loopback-only), so the
# full download -> verify -> replace -> restart flow runs straight off OSS.
#
# Both zips are built with appEnv=BETA so they report "<ver>-beta" and carry the real
# Windows portable path provider (required for the self-update channel to engage). The
# old build reports "<OLD>-beta", which the updater treats as older than the advertised
# "<NEW>", so the changelog update banner appears on launch.
#
# Requirements: Conveyor CLI, Node, the project's Gradle/JDK (same as
# build-test-windows-zip.sh). See doc/en/WindowsZipSelfUpdateTest.md.
#
# Usage: scripts/build-oss-test-update.sh [newVersion] [revision] [outputDir]
#   newVersion  the "new" version to advertise & build; default 2.1.5
#   revision    the "new" revision (any number); default 1
#   outputDir   default output-oss-test
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

NEW_VER="${1:-2.1.5}"
REV="${2:-1}"
OUT_DIR="${3:-output-oss-test}"
# Base URL baked into the OLD build so the packaged app reads it from a JVM system
# property at launch — no `set CROSSPASTE_UPDATE_BASE_URL=...` needed on the test box.
BASE_URL="${4:-https://oss.crosspaste.com/test}"

VERSION_FILE="app/src/desktopMain/resources/crosspaste-version.properties"
OLD_VER="$(sed -n 's/^version=//p' "$VERSION_FILE" | head -1)"
[ -n "$OLD_VER" ] || { echo "ERROR: could not read version from $VERSION_FILE" >&2; exit 1; }

command -v conveyor >/dev/null 2>&1 || {
    echo "ERROR: conveyor CLI not found. Install from https://conveyor.hydraulic.dev" >&2
    exit 1
}
command -v node >/dev/null 2>&1 || { echo "ERROR: node not found" >&2; exit 1; }

if [ "$NEW_VER" = "$OLD_VER" ]; then
    echo "ERROR: newVersion ($NEW_VER) must differ from the current version ($OLD_VER)." >&2
    echo "       The point of the test is to watch About change from $OLD_VER to NEW." >&2
    exit 1
fi

# Build a single BETA windows-zip at whatever version.properties currently says,
# mirroring the CI conveyor prep. Echoes the path of the produced zip.
#   build_zip <stage> [bakeUrl]
# When [bakeUrl] is non-empty, "-Dcrosspaste.update.base.url=<bakeUrl>" is added to
# app.jvm.options so the packaged app reads the override from System.getProperty without
# needing an env var set at launch (a Conveyor-launched app doesn't reliably inherit it).
build_zip() {
    local stage="$1"
    local bake_url="${2:-}"
    # Conveyor consumes app/build/libs/app-desktop-<version>.jar, whose name is the
    # current version. CI builds it via `app:build`; rebuild it here so a bumped
    # version produces a matching jar instead of reusing a stale one.
    echo "==> [$stage] :app:desktopJar (rebuild app-desktop-<version>.jar)" >&2
    ./gradlew -q :app:desktopJar >&2
    echo "==> [$stage] writeConveyorConfig (appEnv=BETA)" >&2
    ./gradlew -q writeConveyorConfig -PappEnv=BETA >&2
    mv app/generated.conveyor.conf generated.conveyor.conf
    node ./.github/scripts/rewriteConveyorConf.js generated.conveyor.conf >&2
    node ./.github/scripts/processJarFiles.js "tesseract-.*-macosx-arm64.jar" generated.conveyor.conf >&2
    node ./.github/scripts/processJarFiles.js "tesseract-.*-macosx-x86_64.jar" generated.conveyor.conf >&2
    node ./.github/scripts/checkDuplicateJars.js generated.conveyor.conf >&2

    if [ -n "$bake_url" ]; then
        echo "==> [$stage] baking -Dcrosspaste.update.base.url=$bake_url into app.jvm.options" >&2
        printf '\napp.jvm.options += "-Dcrosspaste.update.base.url=%s"\n' "$bake_url" >> generated.conveyor.conf
    fi

    local tmp="$OUT_DIR/.build-$stage"
    rm -rf "$tmp"
    echo "==> [$stage] conveyor make windows-zip" >&2
    conveyor make windows-zip --output-dir "$tmp" >&2
    ls "$tmp"/*.zip 2>/dev/null | head -1
}

sha256() {
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        sha256sum "$1" | awk '{print $1}'
    fi
}

# The build gates on a whats-new entry matching the version (verifyChangelogVersion
# task), so the bumped <NEW> build needs a temporary "# [<NEW>] - <date>" header
# prepended to each changelog. Back the originals up and always restore them, along
# with the version file, on exit.
CHANGELOG_DIR="app/src/desktopMain/resources/whats-new"
CHANGELOGS=(en.md zh.md)
BACKUP_DIR="$(mktemp -d)"
for f in "${CHANGELOGS[@]}"; do cp "$CHANGELOG_DIR/$f" "$BACKUP_DIR/$f"; done

restore_state() {
    printf 'version=%s' "$OLD_VER" > "$VERSION_FILE"
    for f in "${CHANGELOGS[@]}"; do cp "$BACKUP_DIR/$f" "$CHANGELOG_DIR/$f"; done
}
trap restore_state EXIT

# Prepend a throwaway "# [<NEW>] - <date>" entry so verifyChangelogVersion passes for
# the bumped build. Restored from backup afterwards — never committed.
inject_changelog_header() {
    local date
    date="$(date +%F)"
    for f in "${CHANGELOGS[@]}"; do
        {
            printf '# [%s] - %s\n\n' "$NEW_VER" "$date"
            printf '## Test build\nLocal self-update test build (not a real release).\n\n'
            cat "$BACKUP_DIR/$f"
        } > "$CHANGELOG_DIR/$f"
    done
}

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

# 1. Old app (current version, e.g. 2.1.4) — install & run; reports <OLD>-beta.
#    The update base URL is baked in, so this build pulls from $BASE_URL on its own.
#    The NEW build deliberately omits it, so the updated app doesn't re-prompt itself.
printf 'version=%s' "$OLD_VER" > "$VERSION_FILE"
OLD_BUILT="$(build_zip old "$BASE_URL")"
[ -f "$OLD_BUILT" ] || { echo "ERROR: old zip not produced" >&2; exit 1; }
OLD_NAME="crosspaste-$OLD_VER-windows-amd64.zip"
cp "$OLD_BUILT" "$OUT_DIR/$OLD_NAME"

# 2. New app (bumped version) — the build the updater downloads; reports <NEW>-beta.
printf 'version=%s' "$NEW_VER" > "$VERSION_FILE"
inject_changelog_header
NEW_BUILT="$(build_zip new)"
[ -f "$NEW_BUILT" ] || { echo "ERROR: new zip not produced" >&2; exit 1; }
NEW_NAME="crosspaste-$NEW_VER-$REV-windows-amd64.zip"
cp "$NEW_BUILT" "$OUT_DIR/$NEW_NAME"

restore_state
trap - EXIT
rm -rf "$BACKUP_DIR" "$OUT_DIR/.build-old" "$OUT_DIR/.build-new"

# 3 + 4. checksum.txt and metadata.properties describing the new zip.
HASH="$(sha256 "$OUT_DIR/$NEW_NAME")"
printf '%s  %s\n' "$HASH" "$NEW_NAME" > "$OUT_DIR/checksum.txt"
printf 'app.version=%s\napp.revision=%s\n' "$NEW_VER" "$REV" > "$OUT_DIR/metadata.properties"

echo
echo "Done. Upload these four files to the OSS test bucket (https://oss.crosspaste.com/test/):"
echo "  $OUT_DIR/$OLD_NAME              <- install & run this one"
echo "  $OUT_DIR/$NEW_NAME"
echo "  $OUT_DIR/checksum.txt"
echo "  $OUT_DIR/metadata.properties"
echo
echo "  app.version=$NEW_VER  app.revision=$REV"
echo "  sha256 ($NEW_NAME) = $HASH"
echo "  update base baked into $OLD_NAME = $BASE_URL"
echo
echo "On the Windows test box — just extract & run, no env var needed:"
printf '%s\n' "  \"<extracted $OLD_NAME>\\bin\\CrossPaste.exe\""
