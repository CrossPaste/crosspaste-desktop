package com.crosspaste.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.app.AppInfo
import com.crosspaste.config.ConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.realm.sync.SyncRuntimeInfoRealm
import com.crosspaste.realm.sync.createSyncRuntimeInfo
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging

class DeviceManager(
    private val appInfo: AppInfo,
    private val configManager: ConfigManager,
    private val syncManager: SyncManager,
    private val syncRuntimeInfoRealm: SyncRuntimeInfoRealm,
) : DeviceListener {

    private val logger = KotlinLogging.logger {}

    private val jsonUtils = getJsonUtils()

    var searching by mutableStateOf(false)

    private val allSyncInfos: MutableMap<String, SyncInfo> = mutableMapOf()

    var syncInfos: MutableList<SyncInfo> = mutableStateListOf()

    private val isSelf: (String) -> Boolean = { appInstanceId ->
        appInstanceId == appInfo.appInstanceId
    }

    private val isNew: (String) -> Boolean = { appInstanceId ->
        !syncManager.getSyncHandlers().keys.contains(appInstanceId)
    }

    private val isBlackListed: (String) -> Boolean = { appInstanceId ->
        val blackSyncInfos: List<SyncInfo> =
            jsonUtils.JSON.decodeFromString(
                configManager.config.blacklist,
            )
        blackSyncInfos.map { it.appInfo.appInstanceId }
            .contains(appInstanceId)
    }

    fun refresh() {
        searching = true
        try {
            syncInfos.clear()
            val existSyncRuntimeInfos =
                allSyncInfos.filter { !isNew(it.key) }
                    .map { createSyncRuntimeInfo(it.value) }
            if (existSyncRuntimeInfos.isNotEmpty()) {
                syncRuntimeInfoRealm.update(existSyncRuntimeInfos)
                    .forEach {
                        syncManager.resolveSync(it)
                    }
            }
            syncInfos.addAll(allSyncInfos.filter { isNew(it.key) && !isBlackListed(it.key) }.values)
        } finally {
            searching = false
        }
    }

    override fun addDevice(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        logger.debug { "Service resolved: $syncInfo" }
        if (!isSelf(appInstanceId)) {
            allSyncInfos[appInstanceId] = syncInfo
            refresh()
        }
    }

    override fun removeDevice(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        logger.debug { "Service removed: $syncInfo" }
        allSyncInfos.remove(appInstanceId)
        refresh()
    }
}
