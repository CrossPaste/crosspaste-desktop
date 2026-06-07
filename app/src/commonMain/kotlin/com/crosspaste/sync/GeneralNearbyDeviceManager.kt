package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.app.RatingPromptManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.KeyedThrottler
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
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
    override val nearbyDeviceScope: CoroutineScope = namedScope(ioDispatcher, "GeneralNearbyDeviceManager"),
) : NearbyDeviceManager {

    private val logger = KotlinLogging.logger {}

    private val jsonUtils = getJsonUtils()

    private val _searching = MutableStateFlow(false)

    override val searching: StateFlow<Boolean> = _searching

    private val syncInfos = MutableStateFlow<Map<String, SyncInfo>>(mapOf())

    // Collapse the burst of serviceResolved callbacks (mDNS cache replay across interfaces)
    // into a single discovery-driven reconnect per device per cooldown window.
    private val reconnectThrottle = KeyedThrottler<String>(FAST_RECONNECT_COOLDOWN_MS)

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
            val now = DateUtils.nowEpochMilliseconds()
            syncInfos.update { current ->
                val existSyncInfo = current[appInstanceId]
                val merged =
                    existSyncInfo?.merge(syncInfo, now) ?: syncInfo.withStampedHostInfo(now)
                current + (appInstanceId to merged)
            }

            syncInfos.value[appInstanceId]?.let { mergedInfo ->
                syncManager.realTimeSyncRuntimeInfos.value
                    .find { it.appInstanceId == appInstanceId }
                    ?.let { existing ->
                        if (existing.diffSyncInfo(mergedInfo)) {
                            syncManager.updateSyncInfo(mergedInfo)
                        }

                        // Discovery-driven fast reconnect: a paired DISCONNECTED peer
                        // just proved reachable (serviceResolved). For a same-IP
                        // reappearance diffSyncInfo is false, so nothing is written to the
                        // DB and the sync state machine never sees it — drive the reconnect
                        // from here. Excludes UNMATCHED / INCOMPATIBLE / UNVERIFIED
                        // ("reachable but rejected"): those are not address-reachability
                        // problems and are left to polling backoff.
                        if (existing.connectState == SyncState.DISCONNECTED &&
                            reconnectThrottle.tryAcquire(appInstanceId, now)
                        ) {
                            syncManager.fastReconnect(appInstanceId)
                        }
                    }
            }

            ratingPromptManager.trackSignificantAction()
        }
    }

    override fun removeDevice(appInstanceId: String) {
        logger.info { "Service removed: $appInstanceId" }
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

    companion object {
        // serviceResolved is an edge signal that arrives in short bursts; one reconnect
        // per device per this window is enough (discovery-driven-fast-reconnect §4.2).
        private const val FAST_RECONNECT_COOLDOWN_MS = 5000L
    }
}
