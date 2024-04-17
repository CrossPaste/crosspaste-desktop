package com.clipevery.sync

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.clipevery.app.AppInfo
import com.clipevery.config.ConfigManager
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.utils.DesktopJsonUtils
import com.clipevery.utils.TxtRecordUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.locks.ReentrantLock
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import javax.jmdns.impl.util.ByteWrangler

class DesktopDeviceManager(
    private val appInfo: AppInfo,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val configManager: ConfigManager,
    private val syncManager: SyncManager,
) : DeviceManager, ServiceListener {

    private val logger = KotlinLogging.logger {}

    private var _searching = mutableStateOf(false)

    override val isSearching: State<Boolean> get() = _searching

    private var _syncInfos: SnapshotStateMap<String, SyncInfo> = mutableStateMapOf()

    override val syncInfos: SnapshotStateMap<String, SyncInfo> get() = _syncInfos

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
        try {
            _searching.value = true
            val newSyncInfos = _syncInfos.filter { isNew(it.key) && !isBlackListed(it.key) }
            _syncInfos.clear()
            _syncInfos.putAll(newSyncInfos)
        } finally {
            _searching.value = false
            lock.unlock()
        }
    }

    override fun serviceAdded(event: ServiceEvent) {
        logger.info { "Service added: " + event.info }
    }

    override fun serviceRemoved(event: ServiceEvent) {
        logger.info { "Service removed: " + event.info }
        val map: Map<String, ByteArray> = mutableMapOf()
        ByteWrangler.readProperties(map, event.info.textBytes)
        val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(map)
        val appInstanceId = syncInfo.appInfo.appInstanceId
        lock.lock()
        try {
            _searching.value = true
            _syncInfos.remove(appInstanceId)
        } finally {
            _searching.value = false
            lock.unlock()
        }
    }

    override fun serviceResolved(event: ServiceEvent) {
        logger.info { "Service resolved: " + event.info }
        val map: Map<String, ByteArray> = mutableMapOf()
        ByteWrangler.readProperties(map, event.info.textBytes)
        val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(map)
        val appInstanceId = syncInfo.appInfo.appInstanceId
        lock.lock()
        try {
            _searching.value = true
            if (!isSelf(appInstanceId)) {
                if (isNew(appInstanceId)) {
                    val isBlackListed = isBlackListed(appInstanceId)
                    if (!isBlackListed) {
                        _syncInfos[appInstanceId] = syncInfo
                    }
                } else {
                    syncRuntimeInfoDao.inertOrUpdate(syncInfo)
                }
            }
        } finally {
            _searching.value = false
            lock.unlock()
        }
    }
}
