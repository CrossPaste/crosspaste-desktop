package com.crosspaste.paste

import com.crosspaste.platform.Platform
import com.crosspaste.sync.SyncManager

/**
 * Platform-independent metadata probed from the system clipboard before
 * reading its payload.
 *
 *  - [types]: macOS pasteboard UTI types, or Linux Wayland MIME types / X11
 *    `TARGETS`.
 *  - [formatProbe]: Windows lock-free format probe (`IsClipboardFormatAvailable`
 *    plus on-demand DWORD reads for password-manager hints).
 */
data class ClipboardMetadata(
    val types: Set<String> = emptySet(),
    val formatProbe: WindowsClipboardFormatProbe? = null,
) {
    fun hasFormat(name: String): Boolean = name in types || formatProbe?.isFormatAvailable(name) == true

    fun hasAnyFormat(names: Collection<String>): Boolean = names.any(::hasFormat)
}

/**
 * Outbound sync policy for a clipboard item captured on this device.
 *
 * Ties together the two persistence/routing flags that must always agree:
 * real-time sync targets ([PasteSourceContext.targetAppInstanceIds]) and the
 * persisted `remote` flag ([PasteSourceContext.remote]), which suppresses both
 * live sync tasks and history-pull export (`AND remote = 0`).
 */
sealed interface ClipboardSyncPolicy {

    /** Normal local copy: push live to all paired peers and export via history pull. */
    data object SyncAll : ClipboardSyncPolicy

    /**
     * Content received over a platform-specific external sync channel (e.g.
     * Apple Universal Clipboard or Xcode device sync): skip live sync to peers
     * on matching platforms so we do not loop back through that channel, while
     * still syncing to peers on other platforms.
     */
    data class SkipPlatforms(
        val shouldSkip: (Platform) -> Boolean,
    ) : ClipboardSyncPolicy

    /**
     * Content received over an OS-agnostic external sync channel (e.g. RustDesk
     * or other remote-desktop / KVM tools): keep in local history only — never
     * push live and persist `remote = true` so history pull never exports it
     * back to any peer.
     */
    data object LocalOnly : ClipboardSyncPolicy

    fun toPasteSourceContext(
        source: String?,
        syncManager: SyncManager,
    ): PasteSourceContext =
        when (this) {
            SyncAll ->
                PasteSourceContext(
                    source = source,
                    remote = false,
                    targetAppInstanceIds = null,
                )
            is SkipPlatforms ->
                PasteSourceContext(
                    source = source,
                    remote = false,
                    targetAppInstanceIds =
                        syncManager
                            .getSyncHandlers()
                            .filterValues { handler -> !shouldSkip(handler.currentSyncRuntimeInfo.platform) }
                            .keys,
                )
            LocalOnly ->
                PasteSourceContext(
                    source = source,
                    remote = true,
                    targetAppInstanceIds = emptySet(),
                )
        }
}

/**
 * Action taken when a [ClipboardConflictRule] matches a clipboard change.
 */
sealed interface ClipboardConflictAction {
    /** Drop immediately before resolving source or reading clipboard contents. */
    data class Ignore(
        val reason: String,
    ) : ClipboardConflictAction

    /**
     * Proceed to source resolution, exclusion check, and capture under
     * [syncPolicy]. When non-null, [sourceOverride] attributes the item
     * directly to the writing tool instead of relying on the foreground window,
     * allowing Source Exclusions to match background writers.
     */
    data class Capture(
        val sourceOverride: String? = null,
        val syncPolicy: ClipboardSyncPolicy = ClipboardSyncPolicy.SyncAll,
    ) : ClipboardConflictAction
}

/**
 * Extensible rule that classifies a clipboard write from its [ClipboardMetadata]
 * before any payload is read.
 *
 * Adding support for another conflicting app or external clipboard-sync tool
 * only requires adding a rule to [ClipboardConflictRules.defaultRules] — no
 * platform service or native macOS Swift edits are needed.
 */
interface ClipboardConflictRule {
    val id: String

    fun match(metadata: ClipboardMetadata): ClipboardConflictAction?
}

/**
 * Declarative [ClipboardConflictRule] that triggers when any format/type in
 * [markerFormats] is present on the clipboard across macOS, Windows, or Linux.
 */
data class FormatConflictRule(
    override val id: String,
    val markerFormats: Set<String>,
    val action: ClipboardConflictAction,
) : ClipboardConflictRule {
    override fun match(metadata: ClipboardMetadata): ClipboardConflictAction? =
        if (metadata.hasAnyFormat(markerFormats)) action else null
}

/**
 * Built-in clipboard conflict and external-sync rules shared across all desktop
 * platforms. Order is significant: self-origin writes are always dropped first,
 * and stricter external-sync rules (OS-agnostic [ClipboardSyncPolicy.LocalOnly])
 * precede platform-scoped rules ([ClipboardSyncPolicy.SkipPlatforms]).
 */
object ClipboardConflictRules {

    const val CROSSPASTE_ORIGIN_ID = "crosspaste_origin"
    const val RUSTDESK_ID = "rustdesk"
    const val APPLE_UNIVERSAL_CLIPBOARD_ID = "apple_universal_clipboard"
    const val APPLE_XCODE_SYNC_ID = "apple_xcode_sync"

    const val MACOS_CROSSPASTE_TYPE = "com.crosspaste"
    const val MACOS_PASTEBOARD_SOURCE_TYPE = "org.nspasteboard.source"
    const val RUSTDESK_OWNER_FORMAT = "dyn.com.rustdesk.owner"
    const val RUSTDESK_SOURCE = "RustDesk"
    const val APPLE_UNIVERSAL_CLIPBOARD_TYPE = "com.apple.is-remote-clipboard"
    const val APPLE_XCODE_SYNC_TYPE = "com.apple.coredevice.dtpasteboardd.sync"

    val SELF_ORIGIN =
        FormatConflictRule(
            id = CROSSPASTE_ORIGIN_ID,
            markerFormats = setOf(MACOS_CROSSPASTE_TYPE, PasteDataFlavors.CROSSPASTE_ORIGIN_MIME),
            action = ClipboardConflictAction.Ignore("crosspaste_origin"),
        )

    val RUSTDESK =
        FormatConflictRule(
            id = RUSTDESK_ID,
            markerFormats = setOf(RUSTDESK_OWNER_FORMAT),
            action =
                ClipboardConflictAction.Capture(
                    sourceOverride = RUSTDESK_SOURCE,
                    syncPolicy = ClipboardSyncPolicy.LocalOnly,
                ),
        )

    val APPLE_UNIVERSAL_CLIPBOARD =
        FormatConflictRule(
            id = APPLE_UNIVERSAL_CLIPBOARD_ID,
            markerFormats = setOf(APPLE_UNIVERSAL_CLIPBOARD_TYPE),
            action =
                ClipboardConflictAction.Capture(
                    syncPolicy = ClipboardSyncPolicy.SkipPlatforms { it.isApple() },
                ),
        )

    val APPLE_XCODE_SYNC =
        FormatConflictRule(
            id = APPLE_XCODE_SYNC_ID,
            markerFormats = setOf(APPLE_XCODE_SYNC_TYPE),
            action =
                ClipboardConflictAction.Capture(
                    syncPolicy = ClipboardSyncPolicy.SkipPlatforms { it.isApple() },
                ),
        )

    val defaultRules: List<ClipboardConflictRule> =
        listOf(
            SELF_ORIGIN,
            RUSTDESK,
            APPLE_UNIVERSAL_CLIPBOARD,
            APPLE_XCODE_SYNC,
        )
}
