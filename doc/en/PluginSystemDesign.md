# Plugin System Design Notes

> Status: design draft (2026-10-10). This document records the feasibility analysis and design conclusions for letting users add custom context-menu actions per clipboard type. Nothing here is implemented yet.
> Chinese version: [PluginSystemDesign.md](../zh/PluginSystemDesign.md)

## 1. Summary

- **Feasible.** The context menu in `DesktopPasteMenuService` is a hard-coded table keyed by `PasteType`, `ContextMenuGroup` already supports submenus, and the Roadmap lists a "Plugin system". The extension point is clear.
- **Desktop phase one uses script / external-command plugins**, not jar plugins. Jar plugins are the most capable but cost several times more (API stability, ClassLoader isolation, jlink trimming, signing). They are deferred to a later phase.
- **The plugin manifest must explicitly declare supported platforms and runtime.** The marketplace, local installation and the context menu share one compatibility check, so users never download a plugin that cannot run.
- **Mobile cannot run process plugins.** Mobile supports only "declarative" plugins (pure data, no code) and system-level share / Shortcuts. If user code is ever needed on mobile, JavaScript is the only policy-safe choice with an engine on both platforms.
- **Manifest model, compatibility check, menu contributor interface and the declarative executor live in `commonMain`**; the process executor lives only in `desktopMain`; script engines are provided per platform via `expect`/`actual`.

## 2. Plugin form comparison

| Form | Pros | Cost | Decision |
|---|---|---|---|
| Script / external command (manifest + child process) | Process isolation, a crash cannot take down the app; any language; reuses the existing CLI as the data interface; no signing issues on macOS | Desktop only; the executor must paper over interpreter differences across the three OSes | **Desktop phase one** |
| Declarative (manifest describes HTTP / template substitution / URL scheme) | No code, works on all platforms; store-review safe | Limited expressiveness | **All platforms, mobile phase one** |
| Embedded JS engine (user code in a sandbox) | All platforms; capability between the two above | Multiple engine bindings and conformance tests to maintain | Phase two, driven by demand |
| Jar plugin (URLClassLoader + ServiceLoader) | Most capable: Compose submenus, direct DAO access | Separately published API module with binary compatibility; ClassLoader isolation; Koin boundary; Conveyor jlink module completion; a jar is full trust | Not now |
| Embedded Lua / Python | — | Size and dependencies; iOS review grey area; Python on Android needs a commercial runtime | No |

## 3. Manifest design

```json
{
  "id": "com.example.translate",
  "version": "1.2.0",
  "minAppVersion": "2.3.0",
  "name": { "en": "Translate", "zh": "翻译" },
  "pasteTypes": ["text", "html"],
  "runtime": "process",
  "headless": false,
  "hosts": ["api.example.com"],
  "platforms": {
    "macos":   { "arch": ["arm64", "x64"], "command": ["/bin/sh", "run.sh"], "requires": ["curl"] },
    "linux":   { "command": ["/bin/sh", "run.sh"], "requires": ["curl"] },
    "windows": { "arch": ["x64"], "command": ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "run.ps1"] }
  },
  "output": "replaceClipboard"
}
```

Field conventions:

- `id`: reverse-DNS, globally unique. `version`: semver, compared with the existing `io.github.z4kn4fein:semver` dependency.
- `minAppVersion`: filtered by both the marketplace index and local installation.
- `name`: locale-to-label map, falls back to English, wired to `GlobalCopywriter` language switching.
- `pasteTypes`: fixed public type identifiers (`text`, `html`, `rtf`, `url`, `color`, `image`, `files`). Once published they must not be renamed.
- `runtime`: `process` (desktop only), `declarative` (all platforms), later `js` (all platforms). The marketplace filters by current platform and by runtime support.
- `headless`: whether the plugin can run under the Linux headless daemon. Plugins whose output is a notification or opening a URL make no sense there.
- `hosts`: allow-list of domains. Shown on the install confirmation page; the executor refuses requests to undeclared hosts.
- `platforms`: **commands are declared per platform**, not "one command + a platform list". A platform that is absent is unsupported. Keys are lowercase `macos` / `windows` / `linux` / `ios` / `android`, mapped once to the wire values in the `Platform` class (`Macos` etc.).
  - `arch` is optional and defaults to all architectures; plugins shipping native binaries must declare it. Raw `Platform.arch` values (`aarch64`, `amd64`, `x86_64`) are normalised to `arm64` / `x64`.
  - `command` is an argv array, never parsed by a shell; relative paths resolve against the plugin directory.
  - `requires`: external dependencies probed on PATH at install or enable time; missing ones mark the plugin "unavailable" with a hint.
- `output`: how the result is handled. Four paths are defined: write back to the clipboard (`PasteboardService.tryWritePasteboard`), create a new paste, show a notification (`NotificationManager`), open a URL (`UISupport.openUrlInBrowser`).

Why per-platform commands: Windows has no bash and no Python by default; macOS stopped bundling python in 12.3 (only via the Xcode command line tools); every Windows has PowerShell 5.1 but not necessarily pwsh. A realistic plugin ships `run.sh` plus `run.ps1`.

## 4. Three layers of compatibility checks

1. **Marketplace index**: generated from all manifests; the client filters by `platforms`, `arch`, `minAppVersion`, `runtime`. Incompatible plugins are hidden or greyed out.
2. **Install time**: the same checks locally plus `requires` probing, so copying a directory by hand cannot bypass the marketplace. Failures show the concrete reason (e.g. "curl missing").
3. **Run time**: the context menu mounts only enabled plugins that passed probing; probe results are cached and can be re-run from the management page.

All three use the same pure function in `commonMain`.

## 5. Platform differences the desktop process executor must absorb

These cannot be expressed in the manifest; the executor must handle them, otherwise every plugin author hits them on every OS:

1. **Encoding**: the protocol is UTF-8 JSON over stdin/stdout. Windows PowerShell 5.1 defaults to the OEM code page, so set `[Console]::OutputEncoding` on launch; inject `PYTHONIOENCODING=utf-8` for Python. Decode as UTF-8 and normalise CRLF.
2. **Paths**: file paths handed to plugins stay in native format; never convert to forward slashes on Windows (see the `NativeMessagingHostService` lessons in `CLAUDE.md`). Non-ASCII user profile paths must be tested.
3. **CLI entry point**: the command name differs per OS (`crosspaste` / `crosspaste-cli`) and is not necessarily on PATH. The executor injects `CROSSPASTE_CLI` with the absolute path of the bundled CLI. Preferably the paste content is fed to the plugin on stdin so most plugins never call the CLI; the data format is the same as the CLI `--json` output.
4. **Timeout and process-tree cleanup**: terminate descendants via `ProcessHandle.descendants()` so Windows and POSIX behave the same.
5. **OS execution restrictions**: macOS quarantine and TCC (section 6); Windows needs `-ExecutionPolicy Bypass` and `.exe` triggers SmartScreen; Linux needs the executable bit and AppImage mount paths change on every run. The installer handles and explains these per OS.
6. **Working directory and environment**: working directory is the plugin directory; the user environment is inherited except for variables the JVM itself sets, plus the explicitly injected ones.
7. **Concurrency and status**: concurrency cap, stderr to the log, an in-progress indicator; `menuScope` already runs on the IO dispatcher and does not block the UI.

## 6. macOS specifics

CrossPaste is packaged by Conveyor, Developer ID signed and notarised, updated by Sparkle. It is not Mac App Store distribution and has no App Sandbox. Script plugins face no OS-level blocker, but:

- **Plugins must live outside the app bundle**, e.g. `~/Library/Application Support/CrossPaste/plugins`. Writing into `.app` breaks the signature, Gatekeeper refuses to launch, and Sparkle delta updates fail.
- **Gatekeeper quarantine**: running through `/bin/sh script.sh` is unaffected; executing a downloaded binary directly is blocked. The management page must explain or strip the attribute on import.
- **TCC inheritance**: plugin child processes run as CrossPaste; touching Desktop, Documents etc. prompts in CrossPaste's name.
- **Hardened Runtime and jars** (jar phase only): pure class loading is fine; jars containing dylibs or JNA natives are blocked by library validation unless entitlements include `disable-library-validation`. Conveyor adds it for JVM apps by default; verify in the release artifact.
- **jlink trimming** (jar phase only): Conveyor packages only JDK modules detected by jdeps. A plugin referencing a module the app does not use fails with `ClassNotFoundException`; add `app.jvm.modules` explicitly in `conveyor.conf`.

Windows MSIX and Linux deb have no extra blockers; MSIX virtualises user-directory writes but they work.

## 7. Code-level obstacles

1. **Menu extension point**: merge the two near-identical `when` tables in `mainPasteMenuItemsProvider` and `sidePasteMenuItemsProvider` of `DesktopPasteMenuService`, then introduce a `PasteMenuContributor` interface and registry that merges plugin items into the base menu. Menu item lambdas are built synchronously on the UI thread, so the plugin list must be cached ahead of time, never read from disk when the menu opens.
2. **Input/output contract**: never expose the internal `PasteItem`. Reads use the CLI JSON; writes use the four output paths in section 3. File and image types pass path lists.
3. **commonMain and mobile compatibility**: manifest model, `PasteMenuContributor`, compatibility check and declarative executor in `commonMain`; the child-process executor only in `desktopMain`. Interfaces must not assume processes exist.
4. **Management UI**: the Extensions page (`ExtensionContentView`) already has MCP, OCR and CLI entries; add a Plugins sub-page: list, enable/disable, open plugin directory, manual reload, re-probe dependencies.
5. **Security**: plugins are off by default and require explicit confirmation on first enable (clipboards often hold passwords and tokens); show declared `hosts` and capabilities; version one supports only local directory installation, no automatic downloads.
6. **Persistence**: store enabled state separately (a SQLDelight table with id, version, enabled, install source, install time), never by editing the manifest file.

Extra work only for jar plugins: a separately published `crosspaste-plugin-api` module with version negotiation, one ClassLoader per plugin whose parent exposes only the API package, exception isolation, Compose version pinning, jlink module completion, macOS native library signing.

## 8. Mobile

### 8.1 Why process plugins are impossible on mobile

- **iOS has no child processes**: no fork/exec, no shell. App Store Review Guideline 2.5.2 forbids downloading and executing code that changes app functionality.
- **Android can technically `exec`, but has no usable environment**: only the toybox shell, no Python, Node or curl. From Android 10, targetSdk 29+ forbids executing files in app-writable storage (W^X). Google Play forbids downloading dex or so files from outside Play but exempts "code that runs in an interpreter with only indirect access to Android APIs".
- **Sandboxed data access**: file pastes on mobile are copies inside the container.
- **Background execution limits**: a plugin can be suspended after a few seconds; long tasks do not fit.

### 8.2 Extension paths that work on mobile

1. **Declarative plugins**: manifest describes an HTTP request template, regex/template substitution, or a URL-scheme hand-off. No code, all platforms, review-safe. Covers "translate", "shorten link", "post to webhook" style needs.
2. **System share**: a "Share to other app" menu item (iOS Share Sheet, Android Intent). On iOS also "Run Shortcut" via `shortcuts://run-shortcut?name=...&input=text`, handing content to the user's own Shortcuts; on Android the equivalent is sharing to automation apps such as Tasker. Zero engine cost.
3. **Sandboxed JS plugins** (phase two, on demand): see 8.4. Apple's 2024 update to guideline 4.7 allows third-party plug-ins running in WebKit / JavaScriptCore provided no native APIs are exposed; re-check the current wording before shipping.

### 8.3 How mobile loads plugins

A declarative plugin is a single JSON file, so loading means download into the sandbox container, parse, validate, register with the menu.

Acquisition paths:

1. **In-app marketplace**: the index lives under the existing `oss.crosspaste.com` domain, sharing the CDN with the desktop update site. The `commonMain` Ktor client fetches and filters the index and downloads the manifest to `plugins/<id>/` in the container. Download, sha256 verification and retry reuse `ModuleLoader` (the OCR model mechanism). Pure data download, no store policy issue.
2. **Sync from a paired desktop**: push declarative plugins that also support mobile over the existing encrypted sync channel with one tap. A differentiating feature, recommended for phase two.
3. **URL scheme and share import**: register the same `crosspaste://` scheme as desktop, support `crosspaste://plugin/install?url=...`, plus a file association (iOS "Open in", Android ACTION_VIEW).
4. **Bundled plugins**: ship a few official declarative plugins in app resources, copied to the plugin directory on first launch, so the menu is never empty and authors have samples.

Runtime loading (all in `commonMain`):

- `PluginRepository` scans the plugin directory with okio FileSystem and `UserDataPathProvider`, parses with kotlinx.serialization, runs the compatibility function and publishes a StateFlow.
- The menu contributor reads that StateFlow. Mobile users cannot edit the directory, so a refresh after install/uninstall is enough; desktop adds a directory watcher or manual reload.
- Declarative executor: HTTP through the Ktor client, template substitution is a small function, opening URLs through `UISupport.openUrlInBrowser`, clipboard writes through `PasteboardService`.

Must be solved specifically:

- **Index signing**: sign the index with a CrossPaste private key, verify with a bundled public key, and carry a sha256 per manifest, so a swapped CDN cannot inject plugins.
- **Network action authorisation**: `hosts` allow-list plus an install confirmation stating "this plugin sends content to api.example.com"; the executor refuses undeclared hosts. Especially important on phones, where users have no other way to audit.

### 8.4 Mobile runtime execution: what each OS supports

| Capability | iOS | Android |
|---|---|---|
| Child process / shell | Impossible | `exec` works technically, but only the toybox shell |
| Run downloaded binaries | No | W^X forbids it from targetSdk 29, effectively no |
| Built-in JS engine | JavaScriptCore, system framework, zero size, callable from Kotlin/Native via `platform.JavaScriptCore` | Only V8 inside WebView, needs a WebView on the main thread, heavy, can be disabled on some devices |
| Embeddable JS engine | Not needed, JSC suffices | QuickJS (native, ~1 MB, ES2023) or Rhino (pure Java, ~1.3 MB, shareable with the desktop JVM) |
| Lua / Python | Lua embeddable but downloaded execution is a review grey area; Python unrealistic | LuaJ works; Python needs Chaquopy, large and commercially licensed |
| Store policy | 2.5.2 forbids downloaded code that changes functionality; 4.7 opens a door for plug-ins inside WebKit / JSC with no native API exposure | Forbids downloaded dex/so; exempts interpreter code with only indirect API access |
| JIT for third-party JS | Not allowed, JSC runs interpreter-only for third-party apps | QuickJS has no JIT; Rhino runs in interpreted mode on Android |

Conclusion: if mobile ever runs user code, **JavaScript** is the only policy-safe choice with an engine on both platforms. Lua and Python are not worth the investment.

JS plugin execution model (identical on all platforms):

1. **A fresh JS context per invocation**, destroyed afterwards. Millisecond cost, in exchange for no state leaking between plugins and no memory growth.
2. **A very narrow injected surface**: an `input` object (same format as the CLI JSON); a `crosspaste` host object exposing only `fetch` (restricted to `hosts`), `setClipboard`, `notify`, `openUrl`. No file system, no DOM, no require. This is both the security boundary and the 4.7 "no native API" requirement.
3. **Fixed entry point**: `main.js` exports `run(input)` returning JSON; the host decides whether to write the clipboard or notify. Plugins never touch the UI directly.
4. **Timeout and memory enforced by the host**: `JSContextGroupSetExecutionTimeLimit` on JSC, an interrupt handler plus `JS_SetMemoryLimit` on QuickJS, `observeInstructionCount` on Rhino. Mobile timeouts must be far shorter than desktop.
5. `commonMain` defines `expect interface ScriptEngine`; the iOS actual wraps JSC, the Android actual wraps QuickJS or Rhino, the desktop actual wraps Rhino. Callers never see the engine.

Engine trade-off: using Rhino on both Android and desktop reduces the engine bindings from three to two (Rhino + JSC), at the cost of Rhino's ES support stopping at ES2015 plus parts of ES2016+, behind QuickJS. Whichever is chosen:

- **Publish a conservative JS subset** (ES2015 recommended, no async/await or Promise).
- **Build a conformance suite**: the same plugin scripts run the same assertions on all three engines in CI. The engines differ in regex, Unicode and Date details; this is the largest hidden cost of the JS phase.

## 9. Phased plan

| Phase | Scope | Platforms |
|---|---|---|
| 1 | Extract `PasteMenuContributor`; existing menus assembled through it; no user-visible change | Desktop |
| 2 | Manifest model + compatibility check + `process` executor + Plugins management page + local directory install | Desktop |
| 3 | Declarative executor + "Share to other app" / "Run Shortcut" menu items | All |
| 4 | Marketplace: signed index, sha256, download install, `crosspaste://plugin/install` | All |
| 5 | Desktop-to-mobile plugin sync | All |
| 6 | Sandboxed JS runtime (`ScriptEngine` expect/actual + conformance suite) | All, started on demand |
| Later | Jar plugins | Desktop, re-evaluated against API demand |

The first PR should cover phase 1 only, so the extension point stabilises first and later work can proceed in independent PRs.
