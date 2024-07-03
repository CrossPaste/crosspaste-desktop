package com.crosspaste.net

import com.crosspaste.dao.sync.HostInfo
import com.crosspaste.dto.sync.SyncInfo

interface SyncInfoFactory {

    fun createSyncInfo(hostInfoFilter: (HostInfo) -> Boolean): SyncInfo
}
