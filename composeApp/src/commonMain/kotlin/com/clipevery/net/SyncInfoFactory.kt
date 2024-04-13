package com.clipevery.net

import com.clipevery.dto.sync.SyncInfo

interface SyncInfoFactory {

    fun createSyncInfo(): SyncInfo
}
