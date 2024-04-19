package com.clipevery.sync

import androidx.compose.runtime.State
import com.clipevery.dto.sync.SyncInfo

interface DeviceManager {

    val isSearching: State<Boolean>

    val syncInfos: MutableList<SyncInfo>

    fun refresh()
}
