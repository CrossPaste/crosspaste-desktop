package com.clipevery.net

import com.clipevery.dao.sync.HostInfo
import com.clipevery.dto.sync.SyncInfo

interface SyncInfoFactory {

    fun createSyncInfo(hostInfoFilter: (HostInfo) -> Boolean): SyncInfo
}
