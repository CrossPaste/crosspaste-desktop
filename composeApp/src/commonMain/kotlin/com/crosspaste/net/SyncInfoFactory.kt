package com.crosspaste.net

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.realm.sync.HostInfo

interface SyncInfoFactory {

    fun createSyncInfo(hostInfoFilter: (HostInfo) -> Boolean): SyncInfo
}
