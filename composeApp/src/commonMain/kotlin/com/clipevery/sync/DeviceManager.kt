package com.clipevery.sync

import com.clipevery.dto.sync.SyncInfo

interface DeviceManager {

    val searching: Boolean

    val syncInfos: MutableList<SyncInfo>

    fun refresh()
}
