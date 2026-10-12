#!/usr/bin/env bash
set -euo pipefail

# Run inside a disposable RPM-based container, on the package's native arch.
# Install from the distro's normal repositories, without development packages.
# Usage: verify_rpm.sh <rpm_file> [repo_dir]
# With repo_dir (output of package_rpm_repo.sh), the package is installed
# through that repository with gpgcheck and repo_gpgcheck enabled, which
# verifies the package and repodata signatures; otherwise the file is
# installed directly.
RPM_FILE=$(realpath "$1")
REPO_DIR=${2:+$(realpath "$2")}
RPM_ARCH=$(rpm -qp --qf '%{ARCH}' "$RPM_FILE")
test "$RPM_ARCH" = "$(uname -m)"
RPM_VERSION=$(rpm -qp --qf '%{VERSION}' "$RPM_FILE")
RPM_RELEASE=$(rpm -qp --qf '%{RELEASE}' "$RPM_FILE")

if [ -n "$REPO_DIR" ]; then
    cat > /etc/yum.repos.d/crosspaste-local.repo <<EOF
[crosspaste-local]
name=CrossPaste (local repository under test)
baseurl=file://$REPO_DIR
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=file://$REPO_DIR/RPM-GPG-KEY-CrossPaste
EOF
    dnf -y install "crosspaste-$RPM_VERSION-$RPM_RELEASE.$RPM_ARCH" desktop-file-utils
else
    dnf -y install "$RPM_FILE" desktop-file-utils
fi
# The package subscribes the system to the production repository, which is
# not reachable from a release build of a version that is not published yet;
# keep it out of the remaining dnf transactions.
dnf() { command dnf --disablerepo=crosspaste "$@"; }
test "$(rpm -q --qf '%{VERSION}-%{RELEASE}' crosspaste)" = "$RPM_VERSION-$RPM_RELEASE"
test "$(readlink /usr/bin/crosspaste)" = /usr/lib/crosspaste/lib/app/bin/crosspaste-cli
# A fresh install is subscribed to the signed dnf repository.
grep -Fx 'baseurl=https://oss.crosspaste.com/site/rpm/' /etc/yum.repos.d/crosspaste.repo
grep -Fx 'gpgcheck=1' /etc/yum.repos.d/crosspaste.repo
grep -Fx 'repo_gpgcheck=1' /etc/yum.repos.d/crosspaste.repo
grep -Fx 'gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-CrossPaste' /etc/yum.repos.d/crosspaste.repo
rpm -qc crosspaste | grep -Fx /etc/yum.repos.d/crosspaste.repo
rpm --import /etc/pki/rpm-gpg/RPM-GPG-KEY-CrossPaste
rpm -K "$RPM_FILE" | grep -F 'digests signatures OK'
test -x /usr/lib/crosspaste/bin/start.sh
test -f /usr/share/metainfo/com.crosspaste.metainfo.xml
desktop-file-validate /usr/share/applications/com.crosspaste.desktop
test "$(crosspaste version)" = "CLI version:  $RPM_VERSION.$RPM_RELEASE"
rpm -V crosspaste

# These are private runtime libraries, not providers for unrelated applications.
if rpm -q --provides crosspaste | grep -q '^lib'; then
    echo 'Unexpected private library Provides' >&2
    exit 1
fi
if rpm -q --requires crosspaste | grep -Eq '^(libjvm|libjava|libcrypto)[.]so'; then
    echo 'Unexpected bundled library Requires' >&2
    exit 1
fi

# Exercise the packaged GUI launcher on an X11 desktop in a private D-Bus
# session. Xvfb is a test dependency; the package supplies its runtime libraries.
dnf -y install xorg-x11-server-Xvfb xorg-x11-xauth dbus-daemon
TEST_DIR=$(mktemp -d)
export XDG_RUNTIME_DIR="$TEST_DIR/runtime"
mkdir -m 0700 "$XDG_RUNTIME_DIR"
export DISPLAY=:99
Xvfb "$DISPLAY" -screen 0 1280x800x24 > "$TEST_DIR/xvfb.log" 2>&1 &
XVFB_PID=$!
APP_PID=
cleanup() {
    if [ -n "$APP_PID" ]; then
        kill "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
    kill "$XVFB_PID" 2>/dev/null || true
    wait "$XVFB_PID" 2>/dev/null || true
    rm -rf "$TEST_DIR"
}
trap cleanup EXIT
sleep 1
kill -0 "$XVFB_PID"
dbus-run-session -- /usr/lib/crosspaste/bin/crosspaste > "$TEST_DIR/desktop.log" 2>&1 &
APP_PID=$!
ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
    if crosspaste --json status > "$TEST_DIR/status.json" 2>/dev/null; then
        ready=true
        break
    fi
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        break
    fi
    sleep 1
done
if [ "$ready" != true ]; then
    cat "$TEST_DIR/desktop.log" >&2
    echo 'Packaged desktop launcher did not become ready' >&2
    exit 1
fi
cat "$TEST_DIR/status.json"
kill "$APP_PID"
wait "$APP_PID" 2>/dev/null || true
APP_PID=

dnf -y remove crosspaste
test ! -e /usr/bin/crosspaste
test ! -e /etc/yum.repos.d/crosspaste.repo
test ! -e /etc/pki/rpm-gpg/RPM-GPG-KEY-CrossPaste
test ! -e /usr/lib/crosspaste
test ! -e /usr/share/applications/com.crosspaste.desktop
test ! -e /usr/share/metainfo/com.crosspaste.metainfo.xml
echo "PASS: $RPM_ARCH installation, repository subscription, CLI, desktop startup, package verification and removal"
