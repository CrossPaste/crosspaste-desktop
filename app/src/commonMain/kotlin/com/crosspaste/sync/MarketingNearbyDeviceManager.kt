package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.LINUX
import com.crosspaste.platform.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MarketingNearbyDeviceManager : NearbyDeviceManager {

    override val searching: StateFlow<Boolean> = MutableStateFlow<Boolean>(false)

    override val syncInfos: StateFlow<List<SyncInfo>> =
        MutableStateFlow(
            listOf(
                SyncInfo(
                    appInfo =
                        AppInfo(
                            appInstanceId = "2",
                            appVersion = "1.1.1",
                            appRevision = "Unknown",
                            userName = "user",
                        ),
                    endpointInfo =
                        EndpointInfo(
                            deviceId = "3",
                            deviceName = "device3",
                            platform =
                                Platform(
                                    name = LINUX,
                                    arch = "arm",
                                    bitMode = 64,
                                    version = "Ubuntu 22.04 LTS",
                                ),
                            hostInfoList = listOf(),
                            port = 0,
                        ),
                ),
            ),
        )

    override fun addDevice(syncInfo: SyncInfo) {
    }

    override fun removeDevice(syncInfo: SyncInfo) {
    }

    override fun refresh() {
    }
}
