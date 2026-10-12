#!/usr/bin/env bash
set -euo pipefail

# Conveyor builds both Linux architectures on the release runner. rpmbuild
# only lays out their precompiled payloads; no target binary is executed.
ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
OUTPUT_DIR=$(realpath "${1:-output}")
BUILD_DIR=$(mktemp -d)
# shellcheck source=.github/scripts/rpm_signing_key.sh
source "$ROOT_DIR/.github/scripts/rpm_signing_key.sh"
trap 'rm -rf "$BUILD_DIR" "$GNUPGHOME"' EXIT
# The packages carry the signing key's public half so a fresh install can
# verify the dnf repository they subscribe to (crosspaste.repo).
gpg --batch --armor --export "$GPG_FINGERPRINT" > "$BUILD_DIR/RPM-GPG-KEY-CrossPaste"
rpm --import "$BUILD_DIR/RPM-GPG-KEY-CrossPaste"

shopt -s nullglob
EXPECTED_VERSION=
for ARCH_PAIR in amd64:x86_64 aarch64:aarch64; do
    ARCH_TOKEN=${ARCH_PAIR%%:*}
    RPM_ARCH=${ARCH_PAIR##*:}
    ARCHIVES=("$OUTPUT_DIR"/crosspaste-*-linux-"$ARCH_TOKEN".tar.gz)
    if [ "${#ARCHIVES[@]}" -ne 1 ]; then
        echo "Error: Expected one Linux $ARCH_TOKEN tarball in $OUTPUT_DIR, found ${#ARCHIVES[@]}" >&2
        exit 1
    fi

    TAR_FILE=${ARCHIVES[0]}
    ARCHIVE_NAME=$(basename "$TAR_FILE")
    if [[ ! $ARCHIVE_NAME =~ ^crosspaste-([0-9]+\.[0-9]+\.[0-9]+)-([0-9]+)-linux-${ARCH_TOKEN}\.tar\.gz$ ]]; then
        echo "Error: Unexpected Conveyor archive name: $ARCHIVE_NAME" >&2
        exit 1
    fi
    APP_VERSION=${BASH_REMATCH[1]}
    APP_REVISION=${BASH_REMATCH[2]}
    FULL_VERSION="$APP_VERSION-$APP_REVISION"
    if [ -n "$EXPECTED_VERSION" ] && [ "$FULL_VERSION" != "$EXPECTED_VERSION" ]; then
        echo "Error: Linux tarballs have different versions: $EXPECTED_VERSION and $FULL_VERSION" >&2
        exit 1
    fi
    EXPECTED_VERSION=$FULL_VERSION

    TOP_DIR="$BUILD_DIR/$RPM_ARCH"
    mkdir -p "$TOP_DIR"/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
    cp "$TAR_FILE" "$ROOT_DIR/app/script/linux_start.sh" "$ROOT_DIR/LICENSE" \
        "$ROOT_DIR/.github/packaging/crosspaste.repo" "$BUILD_DIR/RPM-GPG-KEY-CrossPaste" "$TOP_DIR/SOURCES/"

    # Exclude only capabilities that the payload itself supplies, preserving
    # versioned ELF requirements on glibc, X11, Wayland and other system libs.
    # elfdeps reads either architecture without executing its binaries.
    mkdir -p "$TOP_DIR/private-libs"
    tar -xzf "$TAR_FILE" -C "$TOP_DIR/private-libs" --wildcards '*/lib/*.so*'
    # Match the package's library modes: EL9 elfdeps only emits Requires for
    # files with an execute bit, independently of RPM's classifier settings.
    find "$TOP_DIR/private-libs" -type f -name '*.so*' -exec chmod a+x {} +
    ELFDEPS="$(rpm --eval '%{_rpmconfigdir}')/elfdeps"
    find "$TOP_DIR/private-libs" -type f -name '*.so*' -print0 |
        xargs -0 "$ELFDEPS" --provides | sort -u > "$TOP_DIR/private-provides"
    find "$TOP_DIR/private-libs" -type f -name '*.so*' -print0 |
        xargs -0 "$ELFDEPS" --requires --filter-private | sort -u > "$TOP_DIR/shared-requires"
    comm -23 "$TOP_DIR/shared-requires" "$TOP_DIR/private-provides" > "$TOP_DIR/system-requires"
    # Bracket expressions survive RPM macro expansion, which consumes
    # backslash escapes in values passed through --define.
    PRIVATE_REQUIRES=$(sed 's/[.^$*+?(){}|]/[&]/g' "$TOP_DIR/private-provides" | paste -sd '|' -)
    test -n "$PRIVATE_REQUIRES"
    rm -rf "$TOP_DIR/private-libs"

    rpmbuild -bb "$ROOT_DIR/.github/packaging/crosspaste.spec" \
        --target "$RPM_ARCH" \
        --define "_topdir $TOP_DIR" \
        --define "_tmppath $BUILD_DIR" \
        --define "app_version $APP_VERSION" \
        --define "app_revision $APP_REVISION" \
        --define "private_requires ^($PRIVATE_REQUIRES)$" \
        --define "archive_name $ARCHIVE_NAME"

    RPM_FILE="$TOP_DIR/RPMS/$RPM_ARCH/crosspaste-$APP_VERSION-$APP_REVISION.$RPM_ARCH.rpm"
    rpm --define "_gpg_name $GPG_FINGERPRINT" --define "_gpg_path $GNUPGHOME" --addsign "$RPM_FILE"
    rpm -K "$RPM_FILE" | grep -F 'digests signatures OK'
    rpm -qp --qf '%{NAME} %{VERSION}-%{RELEASE} %{ARCH} %{RSAHEADER:pgpsig}\n' "$RPM_FILE"
    rpm -qp --requires "$RPM_FILE" | sort -u > "$TOP_DIR/package-requires"
    MISSING_REQUIRES=$(comm -23 "$TOP_DIR/system-requires" "$TOP_DIR/package-requires")
    if [ -n "$MISSING_REQUIRES" ]; then
        echo "Error: RPM omitted shared-library system dependencies:" >&2
        echo "$MISSING_REQUIRES" >&2
        exit 1
    fi
    cp "$RPM_FILE" "$OUTPUT_DIR/"
done
