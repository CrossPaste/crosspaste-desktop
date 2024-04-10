package com.clipevery.sync

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.clipevery.config.ConfigManager
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.net.ClipBonjourService
import com.clipevery.utils.JsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging

class DesktopDeviceManager(
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val configManager: ConfigManager,
    private val clipBonjourService: ClipBonjourService,
    private val syncManager: SyncManager,
) : DeviceManager {

    private val logger = KotlinLogging.logger {}

    private var _searching = mutableStateOf(true)

    override val isSearching: State<Boolean> get() = _searching

    private var _syncInfos: SnapshotStateList<SyncInfo> = mutableStateListOf()

    override val syncInfos: SnapshotStateList<SyncInfo> get() = _syncInfos

    private val isNew: (String) -> Boolean = { appInstanceId ->
        !syncManager.getSyncHandlers().keys.contains(appInstanceId)
    }

    private val isBlackListed: (String) -> Boolean = { appInstanceId ->
        val blackSyncInfos: List<SyncInfo> =
            JsonUtils.JSON.decodeFromString(
                configManager.config.blacklist,
            )
        blackSyncInfos.map { it.appInfo.appInstanceId }
            .contains(appInstanceId)
    }

    override suspend fun toSearchNearBy() {
        try {
            _searching.value = true
            _syncInfos.clear()

            var tryNum = 0
            var newSyncInfos: List<SyncInfo> = listOf()
            var existSyncInfos: List<SyncInfo> = listOf()
            try {
                do {
                    tryNum += 1
                    val searchSyncInfos = clipBonjourService.search()
                    if (tryNum == 1 && existSyncInfos.isEmpty()) {
                        existSyncInfos =
                            searchSyncInfos
                                .filter {
                                    !isNew(it.appInfo.appInstanceId) &&
                                        !isBlackListed(it.appInfo.appInstanceId)
                                }
                        syncRuntimeInfoDao.inertOrUpdate(existSyncInfos)
                    }
                    newSyncInfos =
                        searchSyncInfos
                            .filter {
                                isNew(it.appInfo.appInstanceId) &&
                                    !isBlackListed(it.appInfo.appInstanceId)
                            }
                } while (newSyncInfos.isEmpty() && tryNum < 3)
            } catch (e: Exception) {
                logger.error(e) { "search fail" }
            }
            if (newSyncInfos.isNotEmpty()) {
                _syncInfos.addAll(newSyncInfos)
            }
        } finally {
            _searching.value = false
        }
    }
}
