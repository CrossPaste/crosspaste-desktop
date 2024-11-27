package com.crosspaste.sync

import com.crosspaste.dto.sync.SyncInfo
import kotlinx.coroutines.flow.StateFlow

interface DeviceListener {

    val syncInfos: StateFlow<List<SyncInfo>>

    fun addDevice(syncInfo: SyncInfo)

    fun removeDevice(syncInfo: SyncInfo)
}
