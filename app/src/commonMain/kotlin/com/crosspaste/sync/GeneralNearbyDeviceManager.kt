package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.app.RatingPromptManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GeneralNearbyDeviceManager(
    private val appInfo: AppInfo,
    private val configManager: ConfigManager,
    private val ratingPromptManager: RatingPromptManager,
    private val syncManager: SyncManager,
) : NearbyDeviceManager {

    private val logger = KotlinLogging.logger {}

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

    override suspend fun refresh() {
        _searching.value = true
        runCatching {
            val currentAllSyncInfos = _allSyncInfos.value

            // Handle existing sync infos
            val existSyncInfos =
                currentAllSyncInfos
                    .filter { !isNew(it.key) }
                    .map { it.value }

            if (existSyncInfos.isNotEmpty()) {
                for (syncInfo in existSyncInfos) {
                    withContext(ioDispatcher) {
                        syncManager.updateSyncInfo(syncInfo)
                    }
                }
            }

            // Update syncInfos with new, non-blacklisted devices
            val newSyncInfos =
                currentAllSyncInfos
                    .filter { isNew(it.key) && !isBlackListed(it.key) }
                    .values
                    .toList()

            _syncInfos.value = newSyncInfos
        }.apply {
            mainCoroutineDispatcher.launch {
                delay(1500)
                _searching.value = false
            }
        }
    }

    override suspend fun addDevice(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        logger.debug { "Service resolved: $syncInfo" }
        if (!isSelf(appInstanceId)) {
            _allSyncInfos.update { current ->
                current + (appInstanceId to syncInfo)
            }
            refresh()
            ratingPromptManager.trackSignificantAction()
        }
    }

    override suspend fun removeDevice(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        logger.debug { "Service removed: $syncInfo" }
        _allSyncInfos.update { current ->
            current - appInstanceId
        }
        refresh()
        ratingPromptManager.trackSignificantAction()
    }
}
