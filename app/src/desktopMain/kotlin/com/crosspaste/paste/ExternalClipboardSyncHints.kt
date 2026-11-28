package com.crosspaste.paste

/**
 * Clipboard owner markers written by external remote-desktop / clipboard-sync
 * tools when they push content from a remote peer onto the local clipboard.
 *
 * Recognising these markers solves two problems:
 *  - **Source attribution & exclusion**: tools such as RustDesk update the
 *    clipboard in the background (and on Windows via `arboard`, which calls
 *    `OpenClipboard(NULL)`), so foreground-window attribution blames whichever
 *    app happens to be focused and bypasses Source Exclusions for the tool.
 *  - **Sync-loop prevention**: content injected by an external sync channel
 *    already came from another machine. Syncing it back out through CrossPaste
 *    writes it on the remote peer without the external tool's owner marker,
 *    causing the external tool to push it back in an endless rewrite loop.
 */
object ExternalClipboardSyncHints {

    const val RUSTDESK_OWNER_FORMAT = "dyn.com.rustdesk.owner"

    const val RUSTDESK_SOURCE = "RustDesk"

    fun sourceFromWindows(probe: WindowsClipboardFormatProbe): String? =
        if (probe.isFormatAvailable(RUSTDESK_OWNER_FORMAT)) RUSTDESK_SOURCE else null

    fun sourceFromLinux(targets: List<String>): String? =
        if (RUSTDESK_OWNER_FORMAT in targets) RUSTDESK_SOURCE else null
}
