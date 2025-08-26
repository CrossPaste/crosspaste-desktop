package com.crosspaste.ui.devices

import com.crosspaste.dto.sync.SyncInfo

interface SyncScopeFactory {

    fun createSyncScope(syncInfo: SyncInfo): SyncScope
}
