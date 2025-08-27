package com.crosspaste.ui.devices

import com.crosspaste.dto.sync.SyncInfo

interface SyncScope : PlatformScope {

    val syncInfo: SyncInfo
}
