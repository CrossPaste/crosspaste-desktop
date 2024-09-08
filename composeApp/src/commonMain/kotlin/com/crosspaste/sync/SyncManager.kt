package com.crosspaste.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.app.AppInfo
import com.crosspaste.app.VersionCompatibilityChecker
import com.crosspaste.dao.signal.SignalDao
import com.crosspaste.dao.sync.ChangeType
import com.crosspaste.dao.sync.SyncRuntimeInfo
import com.crosspaste.dao.sync.SyncRuntimeInfoDao
import com.crosspaste.dao.sync.SyncState
import com.crosspaste.dao.sync.createSyncRuntimeInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.SyncRefresher
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.signal.SessionBuilderFactory
import com.crosspaste.signal.SignalProcessorCache
import com.crosspaste.signal.SignalProtocolStoreInterface
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SyncManager(
    private val appInfo: AppInfo,
    private val checker: VersionCompatibilityChecker,
    private val telnetHelper: TelnetHelper,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncClientApi: SyncClientApi,
    private val sessionBuilderFactory: SessionBuilderFactory,
    private val signalProtocolStore: SignalProtocolStoreInterface,
    private val signalProcessorCache: SignalProcessorCache,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val signalDao: SignalDao,
    lazyDeviceManager: Lazy<DeviceManager>,
) : SyncRefresher {

    private val logger = KotlinLogging.logger {}

    private val tokenCache: TokenCache = TokenCache

    var realTimeSyncRuntimeInfos: MutableList<SyncRuntimeInfo> = mutableStateListOf()

    private val ignoreVerifySet: MutableSet<String> = mutableSetOf()

    var waitToVerifySyncRuntimeInfo by mutableStateOf<SyncRuntimeInfo?>(null)

    private var internalSyncHandlers: MutableMap<String, SyncHandler> = ConcurrentMap()

    private val realTimeJob = SupervisorJob()

    private val realTimeSyncScope = CoroutineScope(ioDispatcher + realTimeJob)

    private val deviceManager: DeviceManager by lazyDeviceManager

    override var refreshing by mutableStateOf(false)

    private val syncManagerListenJob: Job

    init {
        syncManagerListenJob =
            realTimeSyncScope.launch(CoroutineName("SyncManagerListenChanger")) {
                val syncRuntimeInfos = syncRuntimeInfoDao.getAllSyncRuntimeInfos()
                internalSyncHandlers.putAll(
                    syncRuntimeInfos.map { syncRuntimeInfo ->
                        syncRuntimeInfo.appInstanceId to
                            SyncHandler(
                                appInfo,
                                syncRuntimeInfo,
                                checker,
                                tokenCache,
                                telnetHelper,
                                syncInfoFactory,
                                syncClientApi,
                                sessionBuilderFactory,
                                signalProtocolStore,
                                signalProcessorCache,
                                syncRuntimeInfoDao,
                                signalDao,
                                realTimeSyncScope,
                            )
                    },
                )
                withContext(mainDispatcher) {
                    realTimeSyncRuntimeInfos.addAll(syncRuntimeInfos)
                    refreshWaitToVerifySyncRuntimeInfo()
                    deviceManager.refresh()
                    resolveSyncs()
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
                                    SyncHandler(
                                        appInfo,
                                        insertionSyncRuntimeInfo,
                                        checker,
                                        tokenCache,
                                        telnetHelper,
                                        syncInfoFactory,
                                        syncClientApi,
                                        sessionBuilderFactory,
                                        signalProtocolStore,
                                        signalProcessorCache,
                                        syncRuntimeInfoDao,
                                        signalDao,
                                        realTimeSyncScope,
                                    )
                            }

                            // When the synchronization parameters change,
                            // we will force resolve, so we do not need to process it redundantly here
                            // for (change in changes.changes) { }

                            withContext(mainDispatcher) {
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

    fun refreshWaitToVerifySyncRuntimeInfo() {
        waitToVerifySyncRuntimeInfo =
            realTimeSyncRuntimeInfos
                .filter { !ignoreVerifySet.contains(it.appInstanceId) }
                .firstOrNull { it.connectState == SyncState.UNVERIFIED }
    }

    fun ignoreVerify(appInstanceId: String) {
        ignoreVerifySet.add(appInstanceId)
        refreshWaitToVerifySyncRuntimeInfo()
    }

    fun toVerify(appInstanceId: String) {
        ignoreVerifySet.remove(appInstanceId)
        refreshWaitToVerifySyncRuntimeInfo()
    }

    fun resolveSyncs() {
        internalSyncHandlers.values.forEach { syncHandler ->
            realTimeSyncScope.launch {
                doResolveSync(syncHandler)
            }
        }
    }

    fun resolveSync(id: String) {
        internalSyncHandlers[id]?.let { syncHandler ->
            realTimeSyncScope.launch {
                doResolveSync(syncHandler)
            }
        }
    }

    private suspend fun doResolveSync(syncHandler: SyncHandler) {
        try {
            syncHandler.forceResolve()
        } catch (e: Exception) {
            logger.error(e) { "resolve sync error" }
        }
    }

    fun getSyncHandlers(): Map<String, SyncHandler> {
        return internalSyncHandlers
    }

    fun removeSyncHandler(id: String) {
        realTimeSyncScope.launch(CoroutineName("RemoveSyncHandler")) {
            syncRuntimeInfoDao.deleteSyncRuntimeInfo(id)
        }
    }

    fun trustByToken(
        appInstanceId: String,
        token: Int,
    ) {
        internalSyncHandlers[appInstanceId]?.also { syncHandler ->
            realTimeSyncScope.launch {
                syncHandler.trustByToken(token)
                doResolveSync(syncHandler)
            }
        }
    }

    fun notifyExit() {
        syncManagerListenJob.cancel()
        internalSyncHandlers.values.forEach { syncHandler ->
            // Ensure that the notification is completed before exiting
            runBlocking { syncHandler.notifyExit() }
        }
    }

    fun markExit(appInstanceId: String) {
        internalSyncHandlers[appInstanceId]?.let { syncHandler ->
            realTimeSyncScope.launch(CoroutineName("MarkExit")) {
                syncHandler.markExit()
            }
        }
    }

    fun updateSyncInfo(syncInfo: SyncInfo) {
        realTimeSyncScope.launch(CoroutineName("UpdateSyncInfo")) {
            val newSyncRuntimeInfo = createSyncRuntimeInfo(syncInfo)
            if (syncRuntimeInfoDao.insertOrUpdate(newSyncRuntimeInfo) == ChangeType.NO_CHANGE) {
                internalSyncHandlers[syncInfo.appInfo.appInstanceId]?.tryDirectUpdateConnected()
            }
        }
    }

    override fun refresh() {
        refreshing = true
        realTimeSyncScope.launch(CoroutineName("SyncManagerRefresh")) {
            logger.info { "start launch" }
            try {
                resolveSyncs()
            } catch (e: Exception) {
                logger.error(e) { "checkConnects error" }
            }
            delay(1000)
            logger.info { "set refreshing false" }
            refreshing = false
        }
    }
}
