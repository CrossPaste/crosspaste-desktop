package com.crosspaste.headless

import com.crosspaste.app.UserAttentionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Headless mode has no user-facing windows — every attention signal is
// permanently false. Pollers gated through `launchWhileAttentive` will
// naturally never fire, which is the desired behaviour.
class HeadlessUserAttentionService : UserAttentionService {
    private val never = MutableStateFlow(false)
    override val isMainWindowVisible: StateFlow<Boolean> = never.asStateFlow()
    override val isSearchWindowVisible: StateFlow<Boolean> = never.asStateFlow()
}
