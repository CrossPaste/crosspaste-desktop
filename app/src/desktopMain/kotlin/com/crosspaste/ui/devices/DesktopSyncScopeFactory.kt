package com.crosspaste.ui.devices

import com.crosspaste.dto.sync.SyncInfo

class DesktopSyncScopeFactory : SyncScopeFactory {
    override fun createSyncScope(syncInfo: SyncInfo): SyncScope = DesktopSyncScope(syncInfo)
}
