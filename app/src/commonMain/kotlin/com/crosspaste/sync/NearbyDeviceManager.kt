package com.crosspaste.sync

import com.crosspaste.dto.sync.SyncInfo
import kotlinx.coroutines.flow.StateFlow

interface NearbyDeviceManager {

    val searching: StateFlow<Boolean>

    val syncInfos: StateFlow<List<SyncInfo>>

    suspend fun addDevice(syncInfo: SyncInfo)

    suspend fun removeDevice(syncInfo: SyncInfo)

    suspend fun refresh()
}
