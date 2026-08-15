---
outline: deep
---

# Command-Line Interface

CrossPaste ships with a command-line client that talks to the desktop application running on the same machine. Use it to copy, paste, search, and manage your pasteboard history from the terminal and from shell scripts.

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
| `paste [id]` | Show the most recent paste, or a specific paste by ID. |
| `history` | List recent paste history (`--limit`, `--type`, `--tag`, `--format`). |
| `search <query>` | Search paste history (same filters as `history`). |
| `copy [text]` | Copy text to the clipboard via CrossPaste. Reads stdin when piped. |
| `delete <id>` | Delete a paste by ID. |
| `devices` | List paired devices and their connection state. |
| `config` | View configuration; `config set <key> <value>` changes it. |
| `tags` | Manage paste tags (`create`, `delete`). |
| `version` | Show the CLI version. |

Global options:

- `--json` — machine-readable JSON output for any command.
- `--start` / `--no-start` — when the app is not running, launch it without asking / never launch it. The default is to ask on an interactive terminal, and to fail fast with exit code 3 otherwise (a script is never blocked waiting for input).

Run `crosspaste --help` or `crosspaste <command> --help` for the full reference.

## Piping and scripting

The CLI is built to compose with other tools. Prompts and progress messages go to stderr, so stdout stays clean for pipes.

Copy from a pipe:

```sh
git log -1 --format=%H | crosspaste copy
cat notes.md | crosspaste copy
```

Print raw paste content (no decoration), for piping onward:

```sh
crosspaste paste --raw | pbcopy
crosspaste paste --raw --no-newline > snippet.txt
```

List and bulk-process history:

```sh
# One paste ID per line — combine with xargs
crosspaste history --format id | xargs -n1 crosspaste delete

# JSON output — combine with jq
crosspaste search "TODO" --format json | jq '.items[].preview'
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
