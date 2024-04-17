package com.clipevery.sync

import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.clipevery.dto.sync.SyncInfo

interface DeviceManager {

    val isSearching: State<Boolean>

    val syncInfos: SnapshotStateMap<String, SyncInfo>

    fun refresh()
}
