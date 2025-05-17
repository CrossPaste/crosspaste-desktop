package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform
import com.crosspaste.platform.Platform.Companion.LINUX
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MarketingNearbyDeviceManager : NearbyDeviceManager {

    override val searching: StateFlow<Boolean> = MutableStateFlow(false)

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

    override suspend fun addDevice(syncInfo: SyncInfo) {
    }

    override suspend fun removeDevice(syncInfo: SyncInfo) {
    }

    override suspend fun refresh() {
    }
}
