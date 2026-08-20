---
outline: deep
---

# Command-Line Interface

CrossPaste ships with a command-line client that talks to the CrossPaste application running on the same machine — the desktop app, or the [headless daemon](#headless-daemon-linux-servers) on a server without a graphical session. Use it to copy, paste, search, and manage your pasteboard history from the terminal and from shell scripts.

The CLI is a thin client: every command goes through the local CrossPaste application (over a Unix domain socket), so the terminal always sees the same history, tags, and devices as the UI, and text copied from the CLI syncs to your other devices like any other paste.

## Installation

The CLI binary is bundled with every desktop package. What differs per platform is how it gets on your `PATH`:

| Platform | Terminal command | PATH setup |
|---|---|---|
| macOS | `crosspaste` | On first launch the app offers to install the command; you can also install or repair it any time from **Extensions → Command Line** in the app. This creates a `/usr/local/bin/crosspaste` symlink. |
| Windows (installer / Microsoft Store) | `crosspaste-cli` | Automatic. The package registers a `crosspaste-cli` execution alias (`crosspaste` is reserved for launching the GUI). |
| Linux (deb) | `crosspaste` | Automatic. The package installs a `/usr/bin/crosspaste` symlink. |
| Linux (tarball) | `crosspaste` | Manual: link `<install-dir>/lib/app/bin/crosspaste-cli` into your `PATH`, e.g. `sudo ln -s <install-dir>/lib/app/bin/crosspaste-cli /usr/local/bin/crosspaste`. |
| Windows (zip) | `crosspaste-cli` | Manual: add the extracted `app\bin\` directory to your `PATH`. |
| Linux (AppImage) | — | The binary is bundled at `bin/crosspaste-cli` inside the image, but the mount path changes on every run, so a stable symlink is not possible. Prefer the deb or tarball if you want the CLI. |

The examples below use `crosspaste`; on Windows substitute `crosspaste-cli`.

## Commands

| Command | Description |
|---|---|
| `status` | Show whether the app is running, plus version, device and paste counts. Never auto-starts the app. |
| `paste [id]` | Show the most recent paste, or a specific paste by ID. Image pastes render inline in terminals that support it (iTerm2, WezTerm, Kitty, Ghostty) and always list their stored file paths. |
| `history [query]` | List recent paste history, or search it when query words are given. Filters mirror the search window: `--type` (repeat for several), `--tag`, `--sort newest\|oldest`, plus `--limit` and `--format`. |
| `pick [query]` | Full-screen interactive picker: type to fuzzy-filter, ↑/↓ to select, Enter copies the selected paste (any type) via CrossPaste. Ctrl-E edits it in `$EDITOR`; Ctrl-T/Ctrl-G open a type/tag selector bar where ←/→ choose a value live; Ctrl-S toggles the sort order; Tab toggles a preview panel (image pastes render their first image inline, size-capped, on iTerm2/WezTerm/Kitty/Ghostty); `?` shows help; Esc cancels (exit code 130). |
| `copy [text]` | Copy text via CrossPaste: stores it in history and syncs it to your devices; the system clipboard is set when the desktop app (not the headless daemon) is running. Reads stdin when piped. |
| `edit [id]` | Open the most recent paste (or a specific paste by ID) in `$VISUAL`/`$EDITOR`, and copy the edited result as a new paste. Saving without changes copies nothing. HTML/RTF pastes edit their source markup; the result is always a text paste. |
| `delete <id>` | Delete a paste by ID. |
| `devices` | List paired devices and their connection state. |
| `pair` | Pair with a nearby device by entering the code it displays (see [Pairing from the terminal](#pairing-from-the-terminal)). |
| `config` | View configuration; `config set <key> <value>` changes it. |
| `tags` | Manage paste tags (`create`, `delete`). |
| `version` | Show the CLI version. |

The most common commands have single-letter aliases: `c` = `copy`, `p` = `paste`, `h` = `history`.

Global options:

- `--json` — machine-readable JSON output for any command.
- `--start` / `--no-start` — when the app is not running, launch it without asking / never launch it. The default is to ask on an interactive terminal, and to fail fast with exit code 3 otherwise (a script is never blocked waiting for input).

Run `crosspaste --help` or `crosspaste <command> --help` for the full reference.

## Piping and scripting

The CLI is built to compose with other tools. Prompts and progress messages go to stderr, so stdout stays clean for pipes.

Copy from a pipe — when input is piped and no command is given, the CLI behaves as `copy`, so the command name can be dropped entirely:

```sh
git log -1 --format=%H | crosspaste
cat notes.md | crosspaste
history | grep test | crosspaste copy   # the explicit form works the same
```

Print only the paste content (no decoration), for piping onward. `--raw` reproduces the content exactly as stored — HTML/RTF pastes print their source markup; `--summary` prints the plain-text rendering instead:

```sh
crosspaste paste --raw | pbcopy
crosspaste paste --raw --no-newline > snippet.html
crosspaste paste --summary            # HTML/RTF converted to plain text
```

For an image paste, `--raw` dumps the stored image bytes, so a screenshot copied on any device can be piped straight into a file or another tool. This works for single-image pastes; a paste containing several images fails with the list of stored file paths instead:

```sh
crosspaste paste --raw > screenshot.png
crosspaste paste --raw | magick - -resize 50% small.png
```

On Windows, redirect through cmd — `cmd /c "crosspaste-cli paste --raw > shot.png"` — or use PowerShell 7.4+. Windows PowerShell 5.1's `>` re-encodes native command output as UTF-16 text and corrupts binary data.

List and bulk-process history:

```sh
# One paste ID per line — combine with xargs
crosspaste history --format id | xargs -n1 crosspaste delete

# Search with filters — query words need no quotes
crosspaste history TODO --type text --sort oldest

# JSON output — combine with jq
crosspaste history TODO --format json | jq '.items[].preview'
```

### Exit codes

Scripts can rely on:

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | Error (request failed, paste not found, ...) |
| 2 | Usage error (unknown option, missing argument, ...) |
| 3 | CrossPaste is not running (or still starting) |

For example, `crosspaste status` (which never auto-starts the app) makes a convenient health check:

```sh
if ! crosspaste status > /dev/null; then
  echo "CrossPaste is not ready"
fi
```

## Pairing from the terminal

`crosspaste pair` pairs this machine with another CrossPaste device without touching the UI — the flow every other sync feature builds on:

1. The command lists nearby unpaired devices discovered on the local network (plus devices that are already known but not yet trusted). Pick one by number, or skip the list with `--target <app-instance-id>`.
2. The target device (desktop or mobile — anything with a screen) displays a 6-digit code. Confirm on that device if it asks.
3. Type the code in the terminal (input is hidden). On success the two devices trust each other and start syncing.

Pairing is a human confirmation by design, so `pair` requires an interactive terminal — piping input into it is a usage error. You get up to 5 code attempts per session; on normal exit the CLI cancels an in-progress session, and a session orphaned by an interrupt (Ctrl-C) is reclaimed by a server-side timeout.

If the device list comes up empty, make sure CrossPaste is running on the other device, both machines are on the same network, and multicast/mDNS traffic is not blocked by a firewall.

## Headless daemon (Linux servers)

CrossPaste also runs on machines without a graphical session — a Linux server over SSH, for example. It is the same application started in headless mode (`--headless`): full sync engine, paste history database, and CLI endpoint, but no window and no system-clipboard access. `crosspaste copy` on such a machine adds the content straight to history and syncs it to your paired devices; `crosspaste paste --raw` fetches what you copied elsewhere.

Headless mode is auto-detected: on a machine with no `DISPLAY`/`WAYLAND_DISPLAY` the app enters it even without the flag.

### Getting started

There are two mutually exclusive ways to run the daemon: let the CLI launch it on demand (this section), or have systemd supervise it ([next section](#running-as-a-systemd-user-service)). Pick one — if you want the daemon supervised and started at boot, skip the CLI launch and set up the systemd unit directly.

For the on-demand path: install the deb (or unpack the tarball) as described in [Installation](#installation), then run any command that needs the daemon (`status` is the one exception — it never auto-starts):

```sh
crosspaste history
```

When the daemon is not running, the CLI offers to start it (or use `--start` to skip the question; scripts get exit code 3 instead of a prompt). Then pair with your other devices:

```sh
crosspaste pair
```

The GUI and the daemon are one peer: they share the same data directory and single-instance lock, so exactly one of them runs per user at a time.

### Running as a systemd user service

For a server you want the daemon supervised and started at boot. The daemon is per-user (its data and socket live in your home directory), so use a systemd **user** unit, not a system one.

If a CLI-launched (unmanaged) daemon is already running, stop it **before** enabling the unit — the two are the same single-instance daemon, so each systemd start attempt would otherwise die against the instance lock and `Restart=on-failure` would keep retrying:

```sh
kill "$(cat ~/.local/share/.crosspaste/crosspaste.pid)"   # graceful shutdown (SIGTERM)
```

Create `~/.config/systemd/user/crosspaste.service`:

```ini
[Unit]
Description=CrossPaste headless daemon

[Service]
# deb install path; for a tarball use <install-dir>/bin/crosspaste
ExecStart=/usr/lib/crosspaste/bin/crosspaste --headless
Restart=on-failure

[Install]
WantedBy=default.target
```

Enable it, and allow it to run while you are not logged in:

```sh
systemctl --user daemon-reload
systemctl --user enable --now crosspaste
sudo loginctl enable-linger "$USER"   # start at boot, keep running after logout
```

There is deliberately no `daemon start/stop` CLI subcommand: `systemctl --user stop crosspaste` (or plain SIGTERM) shuts the daemon down gracefully, and `crosspaste status` reports whether it is running.

Once systemd owns the daemon, keep it that way: if the unit is stopped and a CLI command offers to start CrossPaste, decline (or run with `--no-start`) and use `systemctl --user start crosspaste` instead, so the daemon stays supervised.

### macOS (launchd)

A headless macOS machine is rare, but the equivalent is a LaunchAgent. Only use this on a Mac where the desktop app never runs: the app and the daemon are one peer sharing a single-instance lock, so with the GUI running launchd would keep restarting the daemon into that lock. Create `~/Library/LaunchAgents/com.crosspaste.daemon.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>com.crosspaste.daemon</string>
  <key>ProgramArguments</key>
  <array>
    <string>/Applications/CrossPaste.app/Contents/MacOS/CrossPaste</string>
    <string>--headless</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
</dict>
</plist>
```

Then `launchctl load ~/Library/LaunchAgents/com.crosspaste.daemon.plist`.

### Troubleshooting

- `crosspaste status` shows whether the daemon is up; exit code 3 means not running.
- Logs are written to `~/.local/share/.crosspaste/logs/` (on macOS: `~/Library/Application Support/CrossPaste/logs/`).
- A second instance refuses to start with an error on stderr and a non-zero exit — that includes trying to start the daemon while the desktop app is running (the GUI and the daemon are one peer per user).
- Device discovery needs mDNS/multicast on the local network; if `crosspaste pair` finds nothing, check the firewall and that both machines share a network segment.

## Shell completion

The CLI can generate completion scripts for bash, zsh, and fish:

```sh
# bash (~/.bashrc)
source <(crosspaste --generate-completion bash)

# zsh (~/.zshrc)
source <(crosspaste --generate-completion zsh)

# fish
crosspaste --generate-completion fish > ~/.config/fish/completions/crosspaste.fish
```

## Colors

Human-readable output uses a few colors (paste types, device states, app status). Colors are automatically disabled when output is not a terminal, and the [`NO_COLOR`](https://no-color.org) environment variable is respected.

## Development

To test the CLI against a development app instance without packaging a release build:

```sh
# Terminal 1: start the dev app (its user-data dir is app/.user)
./gradlew app:run

# Terminal 2: run the debug CLI against it
./gradlew :cli:run --args="history -n 5"
```

The `:cli:run` task links the host's debug executable and sets `CROSSPASTE_USER_DATA_DIR` to `app/.user`, so the CLI discovers the dev instance's `cli-endpoint.json` instead of the installed app's. An explicit `CROSSPASTE_USER_DATA_DIR` in your environment wins over the default, and the same variable also works when invoking a built CLI binary directly — useful for testing with a real TTY (colors, full terminal width):

```sh
CROSSPASTE_USER_DATA_DIR=app/.user cli/build/bin/macosArm64/debugExecutable/crosspaste-cli.kexe history
```

Output through Gradle is not a TTY, so the CLI cannot detect your window size and falls back to 79 columns. Pass the width explicitly via `COLUMNS` (the CLI honors it whenever stdout is not interactive):

```sh
COLUMNS=$(tput cols) ./gradlew :cli:run --args="history"
```

When the override is set, the CLI never offers to auto-launch the app (that would start the installed one); start the target instance yourself.
