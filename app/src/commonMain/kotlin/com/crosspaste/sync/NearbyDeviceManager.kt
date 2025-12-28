package com.crosspaste.sync

import com.crosspaste.dto.sync.SyncInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface NearbyDeviceManager {

    val nearbyDeviceScope: CoroutineScope

    val nearbySyncInfos: StateFlow<List<SyncInfo>>

    val searching: StateFlow<Boolean>

    fun addDevice(syncInfo: SyncInfo)

    fun removeDevice(syncInfo: SyncInfo)

    fun startSearching()

    fun stopSearching()
}
