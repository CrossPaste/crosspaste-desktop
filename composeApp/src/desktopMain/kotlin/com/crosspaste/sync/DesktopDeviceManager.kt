package com.crosspaste.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.app.AppInfo
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.sync.SyncRuntimeInfoDao
import com.crosspaste.dao.sync.createSyncRuntimeInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.TxtRecordUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.locks.ReentrantLock
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import javax.jmdns.impl.util.ByteWrangler

class DesktopDeviceManager(
    private val appInfo: AppInfo,
    private val configManager: ConfigManager,
    private val syncManager: SyncManager,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
) : DeviceManager, ServiceListener {

    private val logger = KotlinLogging.logger {}

    override var searching by mutableStateOf(false)

    private val allSyncInfos: MutableMap<String, SyncInfo> = mutableMapOf()

    override var syncInfos: MutableList<SyncInfo> = mutableStateListOf()

    private val isSelf: (String) -> Boolean = { appInstanceId ->
        appInstanceId == appInfo.appInstanceId
    }

    private val isNew: (String) -> Boolean = { appInstanceId ->
        !syncManager.getSyncHandlers().keys.contains(appInstanceId)
    }

    private val isBlackListed: (String) -> Boolean = { appInstanceId ->
        val blackSyncInfos: List<SyncInfo> =
            DesktopJsonUtils.JSON.decodeFromString(
                configManager.config.blacklist,
            )
        blackSyncInfos.map { it.appInfo.appInstanceId }
            .contains(appInstanceId)
    }

    private val lock = ReentrantLock()

    override fun refresh() {
        lock.lock()
        searching = true
        try {
            syncInfos.clear()
            val existSyncRuntimeInfos =
                allSyncInfos.filter { !isNew(it.key) }
                    .map { createSyncRuntimeInfo(it.value) }
            if (existSyncRuntimeInfos.isNotEmpty()) {
                syncRuntimeInfoDao.update(existSyncRuntimeInfos)
                    .forEach {
                        syncManager.resolveSync(it)
                    }
            }
            syncInfos.addAll(allSyncInfos.filter { isNew(it.key) && !isBlackListed(it.key) }.values)
        } finally {
            searching = false
            lock.unlock()
        }
    }

    override fun serviceAdded(event: ServiceEvent) {
        logger.debug { "Service added: " + event.info }
    }

    override fun serviceRemoved(event: ServiceEvent) {
        val map: Map<String, ByteArray> = mutableMapOf()
        ByteWrangler.readProperties(map, event.info.textBytes)
        val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(map)
        val appInstanceId = syncInfo.appInfo.appInstanceId
        logger.debug { "Service removed: $syncInfo" }
        lock.lock()
        try {
            allSyncInfos.remove(appInstanceId)
            refresh()
        } finally {
            lock.unlock()
        }
    }

    override fun serviceResolved(event: ServiceEvent) {
        val map: Map<String, ByteArray> = mutableMapOf()
        ByteWrangler.readProperties(map, event.info.textBytes)
        val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(map)
        val appInstanceId = syncInfo.appInfo.appInstanceId
        logger.debug { "Service resolved: $syncInfo" }
        lock.lock()
        try {
            if (!isSelf(appInstanceId)) {
                allSyncInfos[appInstanceId] = syncInfo
                refresh()
            }
        } finally {
            lock.unlock()
        }
    }
}
