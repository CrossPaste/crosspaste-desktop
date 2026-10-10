package com.crosspaste.ui.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.i18n.AbstractGlobalCopywriter
import com.crosspaste.i18n.Copywriter

/**
 * [AbstractGlobalCopywriter] for hosts whose UI is rendered by Compose.
 *
 * Composables call `getText()` directly on the injected `GlobalCopywriter`, so for them to
 * re-render after `switchLanguage()` the read has to go through Compose snapshot state. This
 * class keeps that snapshot mirror out of the i18n layer: the base class stays Compose-free
 * (observable via `languageFlow`), and only Compose-rendered platforms extend this subclass.
 *
 * Writes from non-Compose threads (e.g. `switchLanguage()` from a menu callback) are safe
 * because Compose's global snapshot system handles cross-thread state mutations.
 */
abstract class ComposeGlobalCopywriter(
    configManager: CommonConfigManager,
    factory: (String) -> Copywriter,
) : AbstractGlobalCopywriter(configManager, factory) {

    private var snapshot: Copywriter by mutableStateOf(super.copywriter)

    final override val copywriter: Copywriter
        get() = snapshot

    final override fun onCopywriterChanged(copywriter: Copywriter) {
        snapshot = copywriter
    }
}
