package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.MACOS
import com.crosspaste.platform.Platform
import com.crosspaste.platform.WINDOWS
import com.crosspaste.utils.ioDispatcher
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MarketingSyncManager : SyncManager {

    private val syncRuntimeInfos =
        listOf<SyncRuntimeInfo>(
            SyncRuntimeInfo(
                appInstanceId = "2",
                appVersion = "1.1.1",
                userName = "user",
                deviceId = "2",
                deviceName = "device2",
                platform =
                    Platform(
                        name = MACOS,
                        arch = "arm",
                        bitMode = 64,
                        version = "15.3.1",
                    ),
                noteName = "My Macbook",
                connectState = SyncState.CONNECTED,
            ),
            SyncRuntimeInfo(
                appInstanceId = "1",
                appVersion = "1.1.1",
                userName = "user",
                deviceId = "1",
                deviceName = "device1",
                platform =
                    Platform(
                        name = WINDOWS,
                        arch = "x86",
                        bitMode = 64,
                        version = "11",
                    ),
                noteName = "My Win",
                connectState = SyncState.CONNECTED,
            ),
        )

    override val realTimeSyncRuntimeInfos: StateFlow<List<SyncRuntimeInfo>> =
        MutableStateFlow<List<SyncRuntimeInfo>>(syncRuntimeInfos)

    private var internalSyncHandlers: MutableMap<String, SyncHandler> = ConcurrentMap()

    override val realTimeSyncScope = CoroutineScope(ioDispatcher + SupervisorJob())

    init {
        internalSyncHandlers.putAll(
            syncRuntimeInfos.map { syncRuntimeInfo ->
                syncRuntimeInfo.appInstanceId to createSyncHandler(syncRuntimeInfo)
            },
        )
    }

    override fun createSyncHandler(syncRuntimeInfo: SyncRuntimeInfo): SyncHandler {
        return MarketingSyncHandler(syncRuntimeInfo)
    }

    override fun ignoreVerify(appInstanceId: String) {
    }

    override fun toVerify(appInstanceId: String) {
    }

    override suspend fun resolveSyncs() {
    }

    override suspend fun resolveSync(id: String) {
    }

    override fun trustByToken(
        appInstanceId: String,
        token: Int,
    ) {
    }

    override fun refresh(ids: List<String>) {
    }

    override fun getSyncHandlers(): Map<String, SyncHandler> {
        return internalSyncHandlers
    }

    override fun updateSyncInfo(syncInfo: SyncInfo) {
    }

    override fun removeSyncHandler(id: String) {
    }
}
