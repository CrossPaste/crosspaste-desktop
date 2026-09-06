package com.crosspaste.sync

import com.crosspaste.dto.sync.SyncInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface NearbyDeviceManager {

    val nearbyDeviceScope: CoroutineScope

    val nearbySyncInfos: StateFlow<List<SyncInfo>>

    val searching: StateFlow<Boolean>

    fun addDevice(syncInfo: SyncInfo)

    fun removeDevice(appInstanceId: String)

    /** Hides the device from nearby results until [unblockDevice] is called. */
    fun blockDevice(syncInfo: SyncInfo)

    fun unblockDevice(appInstanceId: String)

    fun startSearching()

    fun stopSearching()
}
