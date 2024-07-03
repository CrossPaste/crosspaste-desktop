package com.crosspaste.sync

import com.crosspaste.dto.sync.SyncInfo

interface DeviceManager {

    val searching: Boolean

    val syncInfos: MutableList<SyncInfo>

    fun refresh()
}
