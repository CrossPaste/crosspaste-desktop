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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    override val searching: StateFlow<Boolean> = _searching.asStateFlow()

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
            val existAppInstanceIds =
                syncRuntimeInfos
                    .map { it.appInstanceId }
                    .toSet()
            infosMap.values
                .filter { existAppInstanceIds.contains(it.appInfo.appInstanceId) }
                .forEach { info ->
                    syncManager.updateSyncInfo(info)
                }
            infosMap.values
                .filter {
                    !existAppInstanceIds.contains(it.appInfo.appInstanceId) &&
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
            val existSyncInfo = syncInfos.value[appInstanceId]
            if (existSyncInfo == null) {
                syncInfos.value += (appInstanceId to syncInfo)
            } else {
                val newSyncInfo = existSyncInfo.merge(syncInfo)
                syncInfos.value += (appInstanceId to newSyncInfo)
            }
            ratingPromptManager.trackSignificantAction()
        }
    }

    override fun removeDevice(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        logger.info { "Service removed: $syncInfo" }
        if (syncInfos.value.containsKey(appInstanceId)) {
            syncInfos.value -= appInstanceId
            ratingPromptManager.trackSignificantAction()
        }
    }
}
