#!/bin/bash
set -e

# Build an AppImage for every Linux tarball Conveyor produced under output/.
# Conveyor emits one tarball per Linux machine, named
#   crosspaste-<version>-linux-<arch>.tar.gz   (arch = amd64 | aarch64)
# so we iterate the known arches instead of assuming a single amd64 archive.
#
# The build runs on an x86_64 runner: the x86_64 appimagetool can package a
# foreign-arch AppDir as long as ARCH is set to the target — it fetches the
# matching runtime (runtime-<arch>) the same way it does for amd64. No qemu or
# arch-specific appimagetool binary is needed.

OUTPUT_DIR="output"
# appimagetool was downloaded into the workspace root by the CI step.
APPIMAGETOOL="$(pwd)/appimagetool.AppImage"

# build_appimage <tar_file> <arch_token> <appimage_arch>
#   arch_token    matches the Conveyor tarball suffix and the output suffix (amd64 | aarch64)
#   appimage_arch the ARCH value appimagetool expects (x86_64 | aarch64)
build_appimage() {
    local TAR_FILE="$1"
    local ARCH_TOKEN="$2"
    local APPIMAGE_ARCH="$3"

    local BUILD_DIR="build/AppDir_${ARCH_TOKEN}"

    # Extract version from filename
    # Example: crosspaste-1.2.4-1730-linux-amd64.tar.gz -> 1.2.4-1730
    local APP_VERSION
    APP_VERSION=$(basename "$TAR_FILE" | sed -E "s/crosspaste-(.*)-linux-${ARCH_TOKEN}.tar.gz/\1/")
    echo "Packaging AppImage: arch=${ARCH_TOKEN} version=${APP_VERSION} (${TAR_FILE})"

    # Prepare the AppDir directory
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"

    echo "Extracting archive..."
    # Use --strip-components=1 to remove the top-level directory
    # (e.g. crosspaste-1.2.4/) and extract contents directly into BUILD_DIR
    tar -xzf "$TAR_FILE" -C "$BUILD_DIR" --strip-components=1

    # Configure AppImage metadata (AppRun, Desktop file, Icon) inside a subshell
    # so the working directory stays at the workspace root for appimagetool.
    (
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
    )

    echo "Building AppImage..."
    # ARCH tells appimagetool the target architecture (and which runtime to use).
    # --appimage-extract-and-run avoids FUSE issues in CI environments.
    ARCH="$APPIMAGE_ARCH" "$APPIMAGETOOL" --appimage-extract-and-run \
        "$BUILD_DIR" \
        "${OUTPUT_DIR}/crosspaste-${APP_VERSION}-${ARCH_TOKEN}.AppImage"

    echo "Success! AppImage generated at: ${OUTPUT_DIR}/crosspaste-${APP_VERSION}-${ARCH_TOKEN}.AppImage"
}

# Map Conveyor's Linux tarball suffix -> appimagetool ARCH value, as
# "<tarball-suffix>:<appimagetool-arch>" pairs (avoids bash 4 associative arrays).
ARCH_PAIRS="amd64:x86_64 aarch64:aarch64"

shopt -s nullglob
built=0
for PAIR in $ARCH_PAIRS; do
    ARCH_TOKEN="${PAIR%%:*}"
    APPIMAGE_ARCH="${PAIR##*:}"
    for TAR_FILE in ${OUTPUT_DIR}/crosspaste-*-linux-${ARCH_TOKEN}.tar.gz; do
        build_appimage "$TAR_FILE" "$ARCH_TOKEN" "$APPIMAGE_ARCH"
        built=1
    done
done

if [ "$built" -eq 0 ]; then
    echo "Error: No crosspaste-*-linux-*.tar.gz archive found in ${OUTPUT_DIR}"
    exit 1
fi
