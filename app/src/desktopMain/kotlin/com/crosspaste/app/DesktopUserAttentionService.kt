package com.crosspaste.app

import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// `WindowInfo.show` reflects the app's *logical* show state — it stays true
// while minimized. If we later decide minimized should count as "not paying
// attention", refine the mapping here; consumers don't need to change.
class DesktopUserAttentionService(
    appWindowManager: DesktopAppWindowManager,
    scope: CoroutineScope = namedScope(ioDispatcher, "DesktopUserAttentionService"),
) : UserAttentionService {

    override val isMainWindowVisible: StateFlow<Boolean> =
        appWindowManager.mainWindowInfo
            .map { it.show }
            .stateIn(scope, SharingStarted.Eagerly, appWindowManager.getCurrentMainWindowInfo().show)

    override val isSearchWindowVisible: StateFlow<Boolean> =
        appWindowManager.searchWindowInfo
            .map { it.show }
            .stateIn(scope, SharingStarted.Eagerly, appWindowManager.getCurrentSearchWindowInfo().show)
}
