# CrossPaste Plugins (desktop)

A plugin adds an entry to the right-click menu of clipboard items of the types it declares. On desktop a plugin is a
directory with a `manifest.json` and a script or program that CrossPaste runs as a child process.

## Installing

1. Open **Extensions → Plugins** and click **Plugin folder**
   (macOS: `~/Library/Application Support/CrossPaste/plugins`; Windows/Linux: `plugins` under the CrossPaste data
   directory).
2. Copy the plugin into its own sub-folder, e.g. `plugins/uppercase/manifest.json`.
3. Click **Reload plugins**, then turn the plugin on. Plugins start disabled; enabling one asks for confirmation and
   shows the network hosts it declares. Replacing a plugin with a different `version` turns it off again until you
   re-confirm.

CrossPaste ships one plugin, **Translate** (a submenu of target languages; the source language is detected), and
copies it into the plugin folder on first launch (disabled, like any
plugin). Its source is in [`app/src/desktopMain/resources/plugins/translate`](../../app/src/desktopMain/resources/plugins/translate)
and needs nothing beyond what each OS ships: JavaScript for Automation on macOS, `python3` on Linux and Windows
PowerShell on Windows. It is copied only once, so deleting or editing it sticks.

[`uppercase/`](uppercase) is a minimal sample with `run.sh` (macOS/Linux) and `run.ps1` (Windows).

## manifest.json

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
  "actions": [
    { "id": "en", "name": { "en": "English" } },
    { "id": "zh-CN", "name": { "zh": "简体中文" } }
  ],
  "output": "replaceClipboard"
}
```

| Field | Meaning |
|---|---|
| `id` | Reverse-domain id, unique across plugins. Letters, digits, `.`, `_`, `-`. |
| `version` | Semver. |
| `minAppVersion` | Optional. Oldest CrossPaste version the plugin works with. |
| `name` | Menu label per language (`en`, `zh`, `zh_hant`, `ja`, ...). Falls back to English. |
| `pasteTypes` | Any of `text`, `html`, `rtf`, `url`, `color`, `image`, `files`. |
| `runtime` | `process` (the only desktop runtime today). |
| `hosts` | Hosts the plugin sends data to. Shown on the enable confirmation; not enforced for process plugins. |
| `platforms` | One entry per supported OS (`macos`, `windows`, `linux`). An OS that is missing is unsupported. |
| `platforms.*.arch` | Optional `arm64` / `x64` list; omit for architecture-independent scripts. |
| `platforms.*.command` | argv, run **without a shell**. The working directory is the plugin directory, so `run.sh` resolves there; an executable path with a separator (`./bin/tool`) is resolved against it too. |
| `platforms.*.requires` | Executables that must be on `PATH`; the plugin is marked unavailable while one is missing. |
| `actions` | Optional. Turns the menu entry into a submenu with one item per action, in the listed order. Each has an `id` (letters, digits, `.`, `_`, `-`), unique within the plugin, and a `name` map (at least one entry, none blank, same language fallback as `name`, then the first declared label). The chosen `id` is passed as `CROSSPASTE_ACTION`. |
| `output` | What CrossPaste does with stdout: `replaceClipboard` (put it on the clipboard), `newPaste` (add it to history without touching the clipboard), `notification` (show it), `openUrl` (open it in the browser). |

## Input and output

- **stdin**: the clicked item as UTF-8 JSON, the same shape as `crosspaste paste --json` with `rawContent` included:
  `id`, `typeName`, `source`, `size`, `tagged`, `createTime`, `remote`, `hash`, `content` (plain-text summary),
  `rawContent` (exact stored text, e.g. HTML markup), `filePaths` (absolute, OS-native paths for `image`/`files`).
- **stdout**: the result as UTF-8. A leading BOM, CRLF line endings and trailing newlines are removed. Empty output is
  reported as an error.
- **stderr**: written to the CrossPaste log. When the process exits non-zero, its tail is shown to the user.
- **Timeout**: 30 seconds, after which the process and all its children are killed.

## Environment

The plugin inherits the user's environment, plus:

- `CROSSPASTE_CLI`: absolute path of the bundled `crosspaste` CLI, when available.
- `CROSSPASTE_LANGUAGE`: the CrossPaste UI language (`en`, `zh`, `zh_hant`, `ja`, `ko`, `de`, `es`, `fr`, `pt`, `fa`), for
  plugins whose output depends on the user's language.
- `CROSSPASTE_ACTION`: the `id` of the submenu item the user picked; unset for plugins without `actions`.
- `PYTHONIOENCODING=utf-8`.

On macOS the app is started by launchd with a minimal `PATH` (`/usr/bin:/bin:/usr/sbin:/sbin`). CrossPaste appends
`/opt/homebrew/bin` and `/usr/local/bin`, both when checking `requires` and when running the plugin, so Homebrew tools
are found either way.

## Platform notes

- **Windows PowerShell 5.1** reads and writes the console in the OEM code page. Read stdin and write stdout and stderr as raw
  UTF-8 bytes, as `uppercase/run.ps1` does.
- **macOS**: running a script through an interpreter (`/bin/sh run.sh`) is not affected by Gatekeeper quarantine;
  a downloaded native binary is.
- **Linux**: scripts run through `/bin/sh` need no executable bit; native binaries do.
