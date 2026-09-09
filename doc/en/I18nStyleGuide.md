# i18n Style Guide

How to name keys and write values in `app/src/desktopMain/resources/i18n/*.properties`.
`I18nConsistencyTest` enforces the mechanical rules; the rest is judgment, and this
document is the reference for that judgment.

## How the files are loaded

- `DesktopCopywriter` loads `/i18n/<lang>.properties` with `java.util.Properties`
  and calls `value.format(*args)` on every lookup, even when no arguments are passed.
  A stray `%` in any value therefore throws the first time that text is shown.
- When the current language is English, a missing key returns an empty string and
  nothing is logged. Other languages fall back to English, and only if English is
  also missing does the UI show `[key]`. A missing English entry is invisible in the
  English UI, which is why the test blocks on it.
- `Properties` silently keeps the last value of a duplicated key and ignores order.
  The test reads the raw lines so duplicates and ordering are caught.
- A value ending in a backslash makes `Properties` treat the next line as a
  continuation, so that line's key vanishes at runtime while still looking like a
  normal entry in the file. The test loads each file with `Properties` exactly as the
  app does and checks that view against the raw lines.

## Mechanical rules (enforced by `I18nConsistencyTest`)

- All ten locales define exactly the same key set.
- No key is defined twice in one file.
- No empty value.
- Keys match `[a-z0-9_?]+`. (`?` is tolerated only for the legacy
  `do_you_trust_this_device?` until it is renamed.)
- Files are sorted by key using plain string order.
- Every value formats with `String.format` using the arguments the English value
  implies: the same count and the same types (`%s` gets a string, `%d` an integer).
  Reusing a slot (`%1$s` twice, or `%<s`) and reordering slots are fine; adding or
  dropping one, or changing `%s` to `%d`, is not.
- Every `getText("literal")` in `commonMain` and `desktopMain` resolves to a key in
  `en.properties`. Missing keys fail the build.
- Keys with no literal reference are printed, not failed. A key may be used by the
  mobile apps (which reuse `commonMain`) or built dynamically at runtime. Confirm both
  before deleting one, then update `MOBILE_ONLY_KEYS` or `DYNAMIC_KEY_PREFIXES` in the
  test.

## Three kinds of key

Before renaming anything, decide which kind a key is.

**Identifier keys** double as a persisted configuration field, a protocol value, or an
enum name. The key is data, not copy. Never rename them; change the value instead, and
if one identifier needs more than one piece of copy, map identifier to copy key in code.

- Shortcut action ids, stored in the user's `shortcut-keys.properties`: `paste`,
  `paste_plain_text`, `paste_primary_type`, `paste_local_last`, `paste_remote_last`,
  `show_main`, `show_search`, `hide_window`, `toggle_pasteboard_monitoring`,
  `toggle_encrypt`
- Theme options: `light`, `system`, `dark`
- Storage statistics type names: `pasteboard`, `text`, `color`, `link`, `html`, `rtf`,
  `image`, `file`
- Cleanup time units: `day`, `week`, `month`, `year`

**Shared keys** are referenced from `commonMain`, which the mobile apps copy. Renaming
one requires the mobile apps to update in the same release cycle. If that cannot be
arranged, keep the old key alongside the new one for one release and delete it after
mobile has switched.

**Desktop-only keys** are referenced only from `desktopMain`. They can be renamed
freely, but rename only keys that are ambiguous, wrong, or hard to maintain. Do not
rename for tidiness.

## Naming keys

- Name the concept in English, in `snake_case`. Keep it short and unambiguous.
- Group by feature with a prefix where one exists: `update_`, `pairing_`, `cli_`,
  `mcp_`, `sync_status_`.
- Suffixes carry fixed meaning: `_title`, `_desc` (explanatory text under a title),
  `_hint` (input placeholder or inline help), `_failed`, `_confirm`. A success state is
  named by its result (`copied`, `saved`), not `_successful`.
- One key, one context. If the same English word is used in two places where another
  language might translate it differently, use two keys.
- Never put a sentence in the key. `no_items_to_export` is a key;
  `please_check_if_the_ip_and_port_are_correct` is not.
- No punctuation in keys.

## Writing English values

English is the source every other language is translated from, so it is written first
and reviewed against where it appears in the UI.

- Sentence case: "Copy failed", not "Copy Failed". Proper nouns keep their casing
  (macOS, PATH, MCP, CrossPaste).
- Periods follow the text's job. Buttons, labels, section titles, and single-sentence
  toasts have none. Explanatory paragraphs and multi-sentence messages end with a
  period.
- Leave out "please" unless the sentence is genuinely a request for a favor.
- Use the single-character ellipsis "…", never "...".
- Do not embed UI symbols in copy (no `FAQ↗`); the UI draws arrows and icons.
- Placeholders are `%s`. Put a comment above the key describing what each placeholder
  is, so translators can reorder them.
- Before changing a value, read the call site. The same old English can mean different
  things in different places; when it does, split the key rather than pick one wording.

Glossary:

| Avoid | Use | Notes |
|---|---|---|
| pasteboard | clipboard | pasteboard is Apple developer vocabulary; user-facing Apple, Microsoft, and Google all say clipboard |
| blacklist | block, blocked devices | |
| remark | nickname | for the user-given name of a paired device |
| token | PIN (pairing v3), pairing code (pairing v2) | name by protocol version, keep only what the screen shows |
| current device | this device | in device lists, the device the user is on |

## Writing other languages

- Translate from the revised English, never from Chinese.
- Translators get the English value, where it appears, what kind of control it is, and
  what each placeholder means.
- Choose words the way the platform does in that language: check the macOS, Windows,
  and Android UI in that language for the same concept.
- Review existing translations before rewriting them. Keep what is accurate and
  natural; rewrite what changed meaning, uses inconsistent terminology, or reads as a
  literal translation.

Chinese (zh, zh_hant):

| Avoid | Use |
|---|---|
| 粘贴板 | 剪贴板 |
| 拷贝 | 复制 |
| 设备ID | 设备 ID (space between Chinese and Latin) |

- Full-width punctuation throughout: `，` `。` `：`.
- Do not open imperative sentences with 请 unless politeness is genuinely needed.
- Keep explanatory text short: "输入目标设备的 IP 和端口", not "请输入目标设备的 IP 地址和端口号以建立连接".
