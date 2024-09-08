package com.crosspaste.sync

import com.crosspaste.dto.sync.SyncInfo

interface DeviceListener {

    fun addDevice(syncInfo: SyncInfo)

    fun removeDevice(syncInfo: SyncInfo)
}
