# Testing the Windows portable-zip self-update (no release needed)

The portable-zip updater downloads a new build, verifies it, and on confirmation
hands off to a batch script that replaces the install directory and relaunches.
The download/verify/extract pipeline is covered by an automated test
(`WindowsZipUpdaterDownloadTest`, runs on any OS). The **apply + restart** step is
Windows-only and must be validated manually — this is how, without publishing a
real release.

## How it works

`WindowsZipUpdater` normally fetches `metadata.properties`, `checksum.txt` and the
zip from GitHub / Aliyun OSS. For testing, set the env var
**`CROSSPASTE_UPDATE_BASE_URL`** to a server hosting those three files:

```
{base}/metadata.properties              app.version / app.revision of the "new" build
{base}/checksum.txt                     "<sha256>  crosspaste-<v>-<r>-windows-amd64.zip"
{base}/crosspaste-<v>-<r>-windows-amd64.zip   the "new" build's zip
```

The base URL comes from the `crosspaste.update.base.url` JVM system property or the
`CROSSPASTE_UPDATE_BASE_URL` env var (property wins). Every build **except PRODUCTION**
honors any URL — including a remote one — so a `BETA` test build can point straight at
an OSS test bucket and run the whole flow with no local server. PRODUCTION accepts only
a **loopback** address (`localhost` / `127.0.0.1`), so a stray override can never
redirect real users to a remote update source.

> Prefer the **system property** for packaged builds: a Conveyor-launched app does not
> reliably inherit a shell `set CROSSPASTE_UPDATE_BASE_URL=...`, but a
> `-Dcrosspaste.update.base.url=...` baked into `app.jvm.options` always reaches
> `System.getProperty`. The one-command script below bakes it in for you.

## Easiest path: one command + OSS (no env var, no local server)

If you have an OSS test bucket reachable at `https://oss.crosspaste.com/test/`, build
both versioned zips and the metadata in one shot:

```bash
scripts/build-oss-test-update.sh 2.1.5 1     # -> output-oss-test/  (newVersion revision)
# args: newVersion revision outputDir baseUrl   (baseUrl default https://oss.crosspaste.com/test)
```

This builds a `2.1.4` "old" zip and a `2.1.5` "new" zip (both `appEnv=BETA`), then
writes `checksum.txt` + `metadata.properties` for the new one. The base URL is **baked
into the old zip** as `-Dcrosspaste.update.base.url=...`, so no env var is needed at
launch. (The new zip omits it on purpose, so the updated app doesn't re-prompt itself.)
Upload all four files from `output-oss-test/` to the bucket, then on the Windows box
just extract and run:

```bat
"C:\path\to\extracted\crosspaste-2.1.4-windows-amd64.zip\bin\CrossPaste.exe"
```

The `2.1.4-beta` old build sees `2.1.5` advertised, the changelog banner appears, and
Download → verify → Restart pulls the new zip from OSS and relaunches as `2.1.5`. The
rest of this doc covers the manual / local-server variant (where the env var does
propagate, e.g. launching the exe from the same cmd that set it).

> ⚠️ **Build with `appEnv=BETA`, not `TEST`.** `appEnv=TEST` makes
> `DesktopAppPathProvider.getAppPathProvider()` return `this` (only valid when the
> provider is mocked in unit tests), which infinite-recurses in `resolve()` and
> crashes a real packaged build on startup (`StackOverflowError`). `BETA` uses the
> real `WindowsAppPathProvider` and still honors a loopback override.

## One-time setup

You only need **one** build to validate the whole download → verify → replace →
restart mechanism: extract a copy of it as the "old" app, and serve the *same* zip
as the "new" release with a higher advertised version. (To also confirm the version
*label* changes, build a second zip with a bumped
`app/src/desktopMain/resources/crosspaste-version.properties` — see the note at the
end.)

### 1. Build a signed BETA Windows zip

```bash
scripts/build-test-windows-zip.sh            # -> output-test/crosspaste-*-windows-amd64.zip
```

Requirements: the [Conveyor CLI](https://conveyor.hydraulic.dev), Node, and the
project's Gradle/JDK. Cross-builds from any host. The app inside reports version
`X.Y.Z-beta`.

**Signing matters — an unsigned/ephemerally-signed build won't run.** Conveyor signs
the Windows binaries with the project's *root signing key*. If that key isn't
configured, Conveyor generates a throwaway key per build; the resulting exe is
silently blocked by Windows (no window, no process, no log). To get a build that
actually launches, either:

- Put the project's real signing key (the CI `SIGNING_KEY` secret) in
  `~/.conveyor/defaults.conveyor.conf` as `app.signing-key = "..."`, then build; or
- Build **on the Windows test machine itself** and run it in place (no
  cross-machine copy → no Mark-of-the-Web block), even with the auto-generated key.

`build.conveyor.conf` is **not** needed for `windows-zip` (it only adds mac
notarization, the GitHub site token, and the Windows Store config).

### 2. Serve it as a "newer" release — on the Windows test machine

Because a BETA build only accepts a loopback override, the server runs on the same
machine. With Git Bash / WSL:

```bash
scripts/serve-fake-update.sh output-test/crosspaste-*-windows-amd64.zip 2.1.5 9999
```

This copies the zip to `crosspaste-2.1.5-9999-windows-amd64.zip`, writes a matching
`checksum.txt` and a `metadata.properties` advertising `2.1.5`, and serves them on
`:8077`.

> No Git Bash? Do it in PowerShell, then `python -m http.server 8077`:
> ```powershell
> cd C:\cptest
> copy crosspaste-*-windows-amd64.zip crosspaste-2.1.5-9999-windows-amd64.zip
> $zip="crosspaste-2.1.5-9999-windows-amd64.zip"
> $h=(Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
> "$h  $zip" | Out-File -Encoding ascii checksum.txt
> "app.version=2.1.5`napp.revision=9999" | Out-File -Encoding ascii metadata.properties
> python -m http.server 8077
> ```

## Run the test

On the Windows machine:

1. Extract the **old** zip somewhere writable (e.g. `C:\Users\you\CrossPaste`).
2. Quit any running CrossPaste first (single-instance: a second launch just exits):
   `taskkill /IM CrossPaste.exe /F`.
3. Launch with the override (same cmd window; CrossPaste is a tray app — no window
   appears, look in the system tray):
   ```bat
   set CROSSPASTE_UPDATE_BASE_URL=http://localhost:8077
   "C:\Users\you\CrossPaste\bin\CrossPaste.exe"
   ```
4. **Confirm it's wired up**: the server window should log `GET /metadata.properties`
   within seconds — that proves the override took effect.
5. Open the **Changelog** screen (tray → open window) — an update banner appears.
   Click **Download update** → progress → verify → extract.
6. Click **Restart & update** → the app quits, the batch script replaces the install
   directory and relaunches.
7. Confirm via **About** that the version is now the "new" one (if you bumped it).

## What to verify

- Channel is detected as portable zip (banner shows only then; Store/installer don't).
- Each state renders: downloading % → verifying → extracting → ready → restarting.
- After restart, the new files are in place and the old process is gone (no two
  instances — the single-instance lock is released cleanly before relaunch).
- A **corrupt download** is rejected: temporarily serve a wrong `checksum.txt`; the
  banner should show a verification failure and not replace anything.
- A **read-only install** (e.g. extracted under `C:\Program Files\...`) should fall
  back rather than attempt replacement.

### Apply / rollback (the Windows-only `apply-update.bat`, must be tested on a real box)

The replace step is driven by `apply-update.bat`. Three things specifically need a real
Windows machine to validate:

- **Spaced paths.** Extract the old zip under a path with a space, ideally a non-ASCII
  user too (e.g. `C:\Users\张 三\CrossPaste`). The whole apply must still work — paths
  are passed to the script via environment variables (`CROSSPASTE_UPDATE_*`), not as
  cmd arguments, precisely so spaces don't get mangled.
- **Full tree + no stale files.** After restart, confirm the install root has *all* of
  `app\ bin\ lib\ conf\ legal\` and that `bin\CrossPaste.exe` runs. The script uses
  `robocopy "%SRC%" "%DST%" /MIR` (whole tree, incl. root files, AND purges files the new
  build removed) — a previous per-directory loop skipped root files, and `/E` would have
  left renamed/deleted jars behind (a classpath-conflict crash risk). **Test a downgrade
  in file set**: build a "new" version that deletes some file, update, and confirm that
  file is gone from the install dir afterward (no stale leftovers).
- **Rollback on failure.** Force an apply failure (e.g. hold an exclusive lock on a file
  under the install dir mid-copy) and confirm: the old install is restored from the
  backup under `%USERPROFILE%\.crosspaste\update\backup`, the *previous* version
  relaunches, and `%USERPROFILE%\.crosspaste\update\apply-update.failed` is written. On
  the next launch the app surfaces a failed-update state (changelog banner) and clears
  the marker — a half-applied update must never look like a success. (The updater copies
  the old install into the update dir as a backup and overwrites the install dir in
  place; it never renames the install dir, which would need write access to its parent.)

## Cleanup

Remove `%USERPROFILE%\.crosspaste\update\` (staging, downloaded zip, `apply-update.bat`,
`apply-update.log`, the `backup\` dir, and any `apply-update.failed` marker) and unset
`CROSSPASTE_UPDATE_BASE_URL`. A successful apply deletes the `backup\` dir itself; if an
apply was interrupted it may remain and can be deleted. If a launch or replace/restart
misbehaves, the two places to look are
`%USERPROFILE%\.crosspaste\logs\crosspaste.log` (startup errors are logged as
"cant start crosspaste") and `%USERPROFILE%\.crosspaste\update\apply-update.log`.

## Security model (known limitation)

The updater downloads `checksum.txt` and the zip from the **same** race-winning mirror
and verifies only **SHA-256**. That protects against corruption and partial downloads,
not against a compromised or MITM'd mirror: anyone who can serve the zip can serve a
matching checksum, and the app would extract and run it with the user's privileges.
Integrity therefore rests on **HTTPS + the GitHub / Aliyun OSS repository ACLs**, not on
the checksum.

The proper hardening is a detached **signature** (e.g. minisign / GPG) over the zip or
checksum, verified against a public key compiled into the app before extraction. Until
that lands, treat the two-mirror race as an availability feature, not an integrity one.

## Notes

- In production builds the override is loopback-only — a stray env var can't redirect
  real users to a remote update source; shipped users update from GitHub / Aliyun OSS.
- Microsoft Store and Conveyor-installer channels are intentionally **not** driven by
  this updater (Store updates via the Store; Conveyor installer self-updates silently).
