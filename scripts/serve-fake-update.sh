#!/usr/bin/env bash
# Serve a fake update "release" (metadata.properties + checksum.txt + the zip,
# renamed to the advertised version) for the Windows portable-zip self-updater,
# so the full update flow can be tested without publishing a real release.
#
# On the Windows test machine, point the app at this server before launching:
#   set CROSSPASTE_UPDATE_BASE_URL=http://<this-host>:<port>
#   "<extracted-old-zip>\bin\CrossPaste.exe"
#
# Requirements: python3 (for the static server), shasum or sha256sum.
#
# Usage: scripts/serve-fake-update.sh <built-zip> <version> <revision> [port]
#   built-zip  zip from build-test-windows-zip.sh (also the "old" app you extract & run)
#   version    the "new" version to advertise; must be > the running app's version
#   revision   the "new" revision (any number, e.g. 9999)
#   port       default 8077
#
# See doc/en/WindowsZipSelfUpdateTest.md for the full test runbook.
set -euo pipefail

ZIP_SRC="${1:?usage: serve-fake-update.sh <built-zip> <version> <revision> [port]}"
VER="${2:?need version}"
REV="${3:?need revision}"
PORT="${4:-8077}"

[ -f "$ZIP_SRC" ] || { echo "ERROR: zip not found: $ZIP_SRC" >&2; exit 1; }

WORK="$(mktemp -d)/release"
mkdir -p "$WORK"
ZIP_NAME="crosspaste-$VER-$REV-windows-amd64.zip"
cp "$ZIP_SRC" "$WORK/$ZIP_NAME"

if command -v shasum >/dev/null 2>&1; then
    HASH="$(shasum -a 256 "$WORK/$ZIP_NAME" | awk '{print $1}')"
else
    HASH="$(sha256sum "$WORK/$ZIP_NAME" | awk '{print $1}')"
fi

printf '%s  %s\n' "$HASH" "$ZIP_NAME" > "$WORK/checksum.txt"
printf 'app.version=%s\napp.revision=%s\n' "$VER" "$REV" > "$WORK/metadata.properties"

echo "Serving fake release from: $WORK"
echo "  metadata.properties : app.version=$VER  app.revision=$REV"
echo "  $ZIP_NAME"
echo "  sha256              : $HASH"
echo
echo "On the Windows test box (replace <this-host> with this machine's LAN IP):"
echo "  set CROSSPASTE_UPDATE_BASE_URL=http://<this-host>:$PORT"
echo
cd "$WORK"
exec python3 -m http.server "$PORT"
