#!/bin/bash
set -e

# 1. Define variables
OUTPUT_DIR="output"
BUILD_DIR="build/AppDir_tmp"

# 2. Find the latest tar.gz package (handle dynamic version names)
# Assume there is only one archive under output, or we just take the newest one
TAR_FILE=$(ls ${OUTPUT_DIR}/crosspaste-*.tar.gz | head -n 1)

if [ -z "$TAR_FILE" ]; then
    echo "Error: No tar.gz file found in ${OUTPUT_DIR}"
    exit 1
fi

echo "Found archive: $TAR_FILE"

# Extract version from filename
# Example: crosspaste-1.2.4-1730-linux-amd64.tar.gz -> 1.2.4-1730
# Assumes the filename format is fixed, using sed to extract
APP_VERSION=$(basename "$TAR_FILE" | sed -E 's/crosspaste-(.*)-linux-amd64.tar.gz/\1/')
echo "Detected Version: $APP_VERSION"

# 3. Prepare the AppDir directory
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

echo "Extracting archive..."
# Use --strip-components=1 to remove the top-level directory
# (e.g. crosspaste-1.2.4/) and extract contents directly into BUILD_DIR
tar -xzf "$TAR_FILE" -C "$BUILD_DIR" --strip-components=1

# 4. Configure AppImage metadata (AppRun, Desktop file, Icon)
cd "$BUILD_DIR"

# Create AppRun
echo '#!/bin/sh
HERE="$(dirname "$(readlink -f "${0}")")"
exec "${HERE}/bin/crosspaste" "$@"' > AppRun
chmod +x AppRun

# Move and fix the .desktop file
# Find the .desktop file (assumed to be under share/applications)
DESKTOP_FILE=$(find share/applications -name "*.desktop" | head -n 1)
cp "$DESKTOP_FILE" .

# Get the desktop filename
DESKTOP_FILENAME=$(basename "$DESKTOP_FILE")

# Key points: adjust Exec and Icon
# Ensure Exec=crosspaste (no path)
# Ensure Icon=crosspaste (no file extension)
sed -i 's|^Exec=.*|Exec=crosspaste|g' "$DESKTOP_FILENAME"
sed -i 's|^Icon=.*|Icon=crosspaste|g' "$DESKTOP_FILENAME"

cp share/icons/hicolor/1024x1024/apps/crosspaste.png .

ln -s crosspaste.png .DirIcon

cd - > /dev/null

echo "Building AppImage..."
# ARCH=x86_64 helps the tool detect the target architecture
# --appimage-extract-and-run avoids FUSE issues in CI environments
ARCH=x86_64 ./appimagetool.AppImage --appimage-extract-and-run \
    "$BUILD_DIR" \
    "${OUTPUT_DIR}/crosspaste-${APP_VERSION}-amd64.AppImage"

echo "Success! AppImage generated at: ${OUTPUT_DIR}/crosspaste-${APP_VERSION}-amd64.AppImage"
