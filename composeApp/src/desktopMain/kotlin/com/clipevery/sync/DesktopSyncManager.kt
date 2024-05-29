package com.clipevery.sync

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dao.sync.SyncState
import com.clipevery.dao.sync.hostInfoListEqual
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.net.SyncInfoFactory
import com.clipevery.net.SyncRefresher
import com.clipevery.net.clientapi.SyncClientApi
import com.clipevery.utils.TelnetUtils
import com.clipevery.utils.cpuDispatcher
import com.clipevery.utils.mainDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.state.SignalProtocolStore

class DesktopSyncManager(
    private val telnetUtils: TelnetUtils,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncClientApi: SyncClientApi,
    private val signalProtocolStore: SignalProtocolStore,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    lazyDeviceManager: Lazy<DeviceManager>,
) : SyncManager, SyncRefresher {

    private val logger = KotlinLogging.logger {}

    private val tokenCache: TokenCache = TokenCache

    override var realTimeSyncRuntimeInfos: MutableList<SyncRuntimeInfo> = mutableStateListOf()

    private val ignoreVerifySet: MutableSet<String> = mutableSetOf()

    override var waitToVerifySyncRuntimeInfo by mutableStateOf<SyncRuntimeInfo?>(null)

    private var internalSyncHandlers: MutableMap<String, SyncHandler> = ConcurrentMap()

    private val realTimeJob = SupervisorJob()

    private val realTimeSyncScope = CoroutineScope(cpuDispatcher + realTimeJob)

    private val deviceManager: DeviceManager by lazyDeviceManager

    private var _refreshing = mutableStateOf(false)

    override val isRefreshing: State<Boolean> get() = _refreshing

    private val resolver = DesktopResolver(realTimeSyncScope)

    init {
        realTimeSyncScope.launch(CoroutineName("SyncManagerListenChanger")) {
            val syncRuntimeInfos = syncRuntimeInfoDao.getAllSyncRuntimeInfos()
            internalSyncHandlers.putAll(
                syncRuntimeInfos.map { syncRuntimeInfo ->
                    syncRuntimeInfo.appInstanceId to
                        DesktopSyncHandler(
                            syncRuntimeInfo,
                            tokenCache,
                            telnetUtils,
                            syncInfoFactory,
                            syncClientApi,
                            signalProtocolStore,
                            syncRuntimeInfoDao,
                        )
                },
            )
            withContext(mainDispatcher) {
                realTimeSyncRuntimeInfos.addAll(syncRuntimeInfos)
                refreshWaitToVerifySyncRuntimeInfo()
                deviceManager.refresh()
            }
            val syncRuntimeInfosFlow = syncRuntimeInfos.asFlow()
            syncRuntimeInfosFlow.collect { changes: ResultsChange<SyncRuntimeInfo> ->
                when (changes) {
                    is UpdatedResults -> {
                        for (deletion in changes.deletions) {
                            val deletionSyncRuntimeInfo = realTimeSyncRuntimeInfos[deletion]
                            internalSyncHandlers.remove(deletionSyncRuntimeInfo.appInstanceId)!!
                                .clearContext()
                        }

                        for (insertion in changes.insertions) {
                            val insertionSyncRuntimeInfo = changes.list[insertion]
                            internalSyncHandlers[insertionSyncRuntimeInfo.appInstanceId] =
                                DesktopSyncHandler(
                                    insertionSyncRuntimeInfo,
                                    tokenCache,
                                    telnetUtils,
                                    syncInfoFactory,
                                    syncClientApi,
                                    signalProtocolStore,
                                    syncRuntimeInfoDao,
                                )
                            resolveSync(insertionSyncRuntimeInfo.appInstanceId, ResolveWay.AUTO_FORCE)
                        }

                        for (change in changes.changes) {
                            val changeSyncRuntimeInfo = changes.list[change]
                            val oldSyncRuntimeInfo = internalSyncHandlers[changeSyncRuntimeInfo.appInstanceId]!!.syncRuntimeInfo
                            internalSyncHandlers[changeSyncRuntimeInfo.appInstanceId]!!
                                .updateSyncRuntimeInfo(changeSyncRuntimeInfo)
                            if (changeSyncRuntimeInfo.connectHostAddress == null ||
                                changeSyncRuntimeInfo.port != oldSyncRuntimeInfo.port ||
                                !hostInfoListEqual(changeSyncRuntimeInfo.hostInfoList, oldSyncRuntimeInfo.hostInfoList)
                            ) {
                                resolveSync(changeSyncRuntimeInfo.appInstanceId, ResolveWay.AUTO_FORCE)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            realTimeSyncRuntimeInfos.clear()
                            realTimeSyncRuntimeInfos.addAll(changes.list)
                            refreshWaitToVerifySyncRuntimeInfo()
                            deviceManager.refresh()
                        }
                    }
                    else -> {
                        // types other than UpdatedResults are not changes -- ignore them
                    }
                }
            }
        }
    }

    override fun refreshWaitToVerifySyncRuntimeInfo() {
        waitToVerifySyncRuntimeInfo =
            realTimeSyncRuntimeInfos
                .filter { !ignoreVerifySet.contains(it.appInstanceId) }
                .firstOrNull { it.connectState == SyncState.UNVERIFIED }
    }

    override fun ignoreVerify(appInstanceId: String) {
        ignoreVerifySet.add(appInstanceId)
        refreshWaitToVerifySyncRuntimeInfo()
    }

    override fun toVerify(appInstanceId: String) {
        ignoreVerifySet.remove(appInstanceId)
        refreshWaitToVerifySyncRuntimeInfo()
    }

    override suspend fun resolveSyncs(resolveWay: ResolveWay) {
        val syncInfo = syncInfoFactory.createSyncInfo()
        internalSyncHandlers.values.forEach { syncHandler ->
            resolver.resolveDevice(syncHandler.syncRuntimeInfo.appInstanceId) {
                doResolveSync(syncHandler, syncInfo, resolveWay)
            }
        }
    }

    override suspend fun resolveSync(
        id: String,
        resolveWay: ResolveWay,
    ) {
        internalSyncHandlers[id]?.let { syncHandler ->
            resolver.resolveDevice(syncHandler.syncRuntimeInfo.appInstanceId) {
                val syncInfo = syncInfoFactory.createSyncInfo()
                doResolveSync(syncHandler, syncInfo, resolveWay)
            }
        }
    }

    private suspend fun doResolveSync(
        syncHandler: SyncHandler,
        currentDeviceSyncInfo: SyncInfo,
        resolveWay: ResolveWay,
    ) {
        try {
            syncHandler.resolveSync(currentDeviceSyncInfo, resolveWay)
        } catch (e: Exception) {
            logger.error(e) { "resolve sync error" }
        }
    }

    override fun getSyncHandlers(): Map<String, SyncHandler> {
        return internalSyncHandlers
    }

    override suspend fun trustByToken(
        appInstanceId: String,
        token: Int,
    ) {
        internalSyncHandlers[appInstanceId]?.also { syncHandler ->
            resolver.resolveDevice(syncHandler.syncRuntimeInfo.appInstanceId) {
                syncHandler.trustByToken(token)
                val syncInfo = syncInfoFactory.createSyncInfo()
                syncHandler.resolveSync(syncInfo, ResolveWay.AUTO_FORCE)
            }
        }
    }

    override fun notifyExit() {
        internalSyncHandlers.values.forEach { syncHandler ->
            // Ensure that the notification is completed before exiting
            runBlocking { syncHandler.notifyExit() }
        }
    }

    override fun markExit(appInstanceId: String) {
        internalSyncHandlers[appInstanceId]?.let { syncHandler ->
            realTimeSyncScope.launch(CoroutineName("MarkExit")) {
                syncHandler.markExit()
            }
        }
    }

    override fun refresh() {
        _refreshing.value = true
        realTimeSyncScope.launch(CoroutineName("SyncManagerRefresh")) {
            logger.info { "start launch" }
            try {
                resolveSyncs(ResolveWay.MANUAL)
            } catch (e: Exception) {
                logger.error(e) { "checkConnects error" }
            }
            delay(1000)
            logger.info { "set refreshing false" }
            _refreshing.value = false
        }
    }
}
