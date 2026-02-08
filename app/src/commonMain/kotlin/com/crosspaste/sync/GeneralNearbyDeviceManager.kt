package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.app.RatingPromptManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class GeneralNearbyDeviceManager(
    private val appInfo: AppInfo,
    configManager: CommonConfigManager,
    private val ratingPromptManager: RatingPromptManager,
    private val syncManager: SyncManager,
    override val nearbyDeviceScope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob()),
) : NearbyDeviceManager {

    private val logger = KotlinLogging.logger {}

    private val jsonUtils = getJsonUtils()

    private val _searching = MutableStateFlow(false)

    override val searching: StateFlow<Boolean> = _searching

    private val syncInfos = MutableStateFlow<Map<String, SyncInfo>>(mapOf())

    private val isSelf: (String) -> Boolean = { appInstanceId ->
        appInstanceId == appInfo.appInstanceId
    }

    private val blackSyncInfos =
        configManager.config
            .map {
                buildBlackSyncInfoMap(it.blacklist)
            }.stateIn(
                scope = nearbyDeviceScope,
                started = SharingStarted.Eagerly,
                initialValue =
                    buildBlackSyncInfoMap(
                        configManager.getCurrentConfig().blacklist,
                    ),
            )

    override val nearbySyncInfos: StateFlow<List<SyncInfo>> =
        combine(
            syncInfos,
            syncManager.realTimeSyncRuntimeInfos,
            blackSyncInfos,
        ) { infosMap, syncRuntimeInfos, blackSyncInfos ->
            val existSyncMap = syncRuntimeInfos.associateBy { info -> info.appInstanceId }

            infosMap.values
                .forEach { info ->
                    existSyncMap[info.appInfo.appInstanceId]?.diffSyncInfo(info)?.let {
                        if (it) {
                            syncManager.updateSyncInfo(info)
                        }
                    }
                }
            infosMap.values
                .filter {
                    !existSyncMap.contains(it.appInfo.appInstanceId) &&
                        !blackSyncInfos.contains(it.appInfo.appInstanceId) &&
                        !isSelf(it.appInfo.appInstanceId)
                }.sortedBy { it.appInfo.appInstanceId }
        }.stateIn(
            scope = nearbyDeviceScope,
            started = SharingStarted.Eagerly,
            initialValue = listOf(),
        )

    private fun buildBlackSyncInfoMap(blacklist: String): Map<String, SyncInfo> =
        jsonUtils.JSON
            .decodeFromString<List<SyncInfo>>(blacklist)
            .associateBy { it.appInfo.appInstanceId }

    override fun addDevice(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        if (!isSelf(appInstanceId)) {
            logger.info { "Service resolved: $syncInfo" }
            syncInfos.update { current ->
                val existSyncInfo = current[appInstanceId]
                if (existSyncInfo == null) {
                    current + (appInstanceId to syncInfo)
                } else {
                    current + (appInstanceId to existSyncInfo.merge(syncInfo))
                }
            }
            ratingPromptManager.trackSignificantAction()
        }
    }

    override fun removeDevice(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        logger.info { "Service removed: $syncInfo" }
        var removed = false
        syncInfos.update { current ->
            if (current.containsKey(appInstanceId)) {
                removed = true
                current - appInstanceId
            } else {
                current
            }
        }
        if (removed) {
            ratingPromptManager.trackSignificantAction()
        }
    }

    override fun startSearching() {
        _searching.value = true
    }

    override fun stopSearching() {
        _searching.value = false
    }
}
