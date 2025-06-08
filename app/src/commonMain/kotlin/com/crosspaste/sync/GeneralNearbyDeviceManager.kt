package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.app.RatingPromptManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.getControlUtils
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GeneralNearbyDeviceManager(
    private val appInfo: AppInfo,
    private val configManager: ConfigManager,
    private val ratingPromptManager: RatingPromptManager,
    private val syncManager: SyncManager,
) : NearbyDeviceManager {

    private val logger = KotlinLogging.logger {}

    private val controlUtils = getControlUtils()

    private val jsonUtils = getJsonUtils()

    private val _searching = MutableStateFlow(false)

    override val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _allSyncInfos = MutableStateFlow<Map<String, SyncInfo>>(mapOf())

    private val _syncInfos = MutableStateFlow<List<SyncInfo>>(listOf())
    override val syncInfos = _syncInfos.asStateFlow()

    private val isSelf: (String) -> Boolean = { appInstanceId ->
        appInstanceId == appInfo.appInstanceId
    }

    private val isNew: (String) -> Boolean = { appInstanceId ->
        !syncManager.getSyncHandlers().keys.contains(appInstanceId)
    }

    private val isBlackListed: (String) -> Boolean = { appInstanceId ->
        val blackSyncInfos: List<SyncInfo> =
            jsonUtils.JSON.decodeFromString(
                configManager.getCurrentConfig().blacklist,
            )
        blackSyncInfos.map { it.appInfo.appInstanceId }
            .contains(appInstanceId)
    }

    override fun refreshSyncManager() {
        _searching.value = true
        syncManager.realTimeSyncScope.launch {
            controlUtils.ensureMinExecutionTime(delayTime = 1500L) {
                val currentAllSyncInfos = _allSyncInfos.value

                // Handle existing sync infos
                val existSyncInfos =
                    currentAllSyncInfos
                        .filter { !isNew(it.key) }
                        .map { it.value }

                if (existSyncInfos.isNotEmpty()) {
                    for (syncInfo in existSyncInfos) {
                        syncManager.updateSyncInfo(syncInfo, refresh = true)
                    }
                }

                // Update syncInfos with new, non-blacklisted devices
                val newSyncInfos =
                    currentAllSyncInfos
                        .filter { isNew(it.key) && !isBlackListed(it.key) }
                        .values
                        .toList()

                _syncInfos.value = newSyncInfos
            }
            _searching.value = false
        }
    }

    override fun updateSyncManager() {
        val currentAllSyncInfos = _allSyncInfos.value

        // Handle existing sync infos
        val existSyncInfos =
            currentAllSyncInfos
                .filter { !isNew(it.key) }
                .map { it.value }

        if (existSyncInfos.isNotEmpty()) {
            for (syncInfo in existSyncInfos) {
                syncManager.updateSyncInfo(syncInfo, refresh = false)
            }
        }

        // Update syncInfos with new, non-blacklisted devices
        val newSyncInfos =
            currentAllSyncInfos
                .filter { isNew(it.key) && !isBlackListed(it.key) }
                .values
                .toList()

        _syncInfos.value = newSyncInfos
    }

    override fun addDevice(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        logger.debug { "Service resolved: $syncInfo" }
        if (!isSelf(appInstanceId)) {
            _allSyncInfos.update { current ->
                current + (appInstanceId to syncInfo)
            }
            refreshSyncManager()
            ratingPromptManager.trackSignificantAction()
        }
    }

    override fun removeDevice(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        logger.debug { "Service removed: $syncInfo" }
        _allSyncInfos.update { current ->
            current - appInstanceId
        }
        refreshSyncManager()
        ratingPromptManager.trackSignificantAction()
    }
}
