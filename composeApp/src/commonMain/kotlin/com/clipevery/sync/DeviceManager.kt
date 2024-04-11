package com.clipevery.sync

import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.clipevery.dto.sync.SyncInfo

interface DeviceManager {

    val isSearching: State<Boolean>
    val syncInfos: SnapshotStateList<SyncInfo>

    suspend fun toSearchNearBy()

    fun removeSyncInfo(appInstanceId: String)
}
