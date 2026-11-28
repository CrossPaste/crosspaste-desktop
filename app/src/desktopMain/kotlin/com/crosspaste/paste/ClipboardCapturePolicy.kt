package com.crosspaste.paste

import com.crosspaste.sync.SyncManager

/**
 * Result of evaluating a clipboard change against [ClipboardCapturePolicy].
 */
sealed interface ClipboardCaptureDecision {
    /** Do not read or persist the clipboard contents. */
    data class Ignore(
        val reason: String,
    ) : ClipboardCaptureDecision

    /** Proceed to read the clipboard and consume it with [sourceContext]. */
    data class Capture(
        val sourceContext: PasteSourceContext,
        val matchedRuleId: String? = null,
    ) : ClipboardCaptureDecision
}

/**
 * Unified pre-read decision engine shared by macOS, Windows, Linux Wayland, and
 * Linux X11 pasteboard services.
 *
 * Evaluation order is invariant across platforms:
 * 1. [conflictRules] (all lock-free format checks): immediately drop self-origin
 *    writes, or pick up an authoritative [ClipboardConflictAction.Capture]
 *    (source override + [ClipboardSyncPolicy]).
 * 2. Source resolution & [DesktopSourceExclusionService]: resolves
 *    `sourceOverride ?: resolveExplicitSource() ?: resolveFallbackSource()`.
 *    Running exclusion before [PasswordManagerHints] keeps excluded Windows apps
 *    from even having `OpenClipboard` called for DWORD probes.
 * 3. [PasswordManagerHints]: drop concealed/transient secrets on all platforms.
 * 4. Optional [conflictNotifiers] (e.g. [UniversalClipboardHint]) and
 *    [ClipboardSyncPolicy.toPasteSourceContext] assembly.
 */
class ClipboardCapturePolicy(
    private val sourceExclusionService: DesktopSourceExclusionService,
    private val syncManager: SyncManager,
    private val conflictRules: List<ClipboardConflictRule> = ClipboardConflictRules.defaultRules,
    private val conflictNotifiers: Map<String, suspend () -> Unit> = emptyMap(),
) {

    suspend fun evaluate(
        metadata: ClipboardMetadata,
        resolveExplicitSource: suspend () -> String? = { null },
        resolveFallbackSource: suspend () -> String?,
    ): ClipboardCaptureDecision {
        var matchedRuleId: String? = null
        var captureAction = ClipboardConflictAction.Capture()
        for (rule in conflictRules) {
            val action = rule.match(metadata) ?: continue
            when (action) {
                is ClipboardConflictAction.Ignore -> return ClipboardCaptureDecision.Ignore(action.reason)
                is ClipboardConflictAction.Capture -> {
                    matchedRuleId = rule.id
                    captureAction = action
                    break
                }
            }
        }

        val source =
            captureAction.sourceOverride
                ?: resolveExplicitSource()
                ?: resolveFallbackSource()
        if (sourceExclusionService.isExcluded(source)) {
            return ClipboardCaptureDecision.Ignore("excluded_source:$source")
        }

        if (PasswordManagerHints.isConcealed(metadata)) {
            return ClipboardCaptureDecision.Ignore("concealed")
        }

        matchedRuleId?.let { conflictNotifiers[it]?.invoke() }

        return ClipboardCaptureDecision.Capture(
            sourceContext = captureAction.syncPolicy.toPasteSourceContext(source, syncManager),
            matchedRuleId = matchedRuleId,
        )
    }
}
