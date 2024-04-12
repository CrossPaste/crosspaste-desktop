package com.clipevery.sync

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dao.sync.hostInfoListEqual
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
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.state.SignalProtocolStore

class DesktopSyncManager(
    private val telnetUtils: TelnetUtils,
    private val syncClientApi: SyncClientApi,
    private val signalProtocolStore: SignalProtocolStore,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
) : SyncManager, SyncRefresher {

    private val logger = KotlinLogging.logger {}

    private val tokenCache: TokenCache = TokenCache

    override var realTimeSyncRuntimeInfos: MutableList<SyncRuntimeInfo> = mutableStateListOf()

    private var internalSyncHandlers: MutableMap<String, SyncHandler> = ConcurrentMap()

    private val realTimeJob = SupervisorJob()

    private val realTimeSyncScope = CoroutineScope(cpuDispatcher + realTimeJob)

    private var _refreshing = mutableStateOf(false)

    override val isRefreshing: State<Boolean> get() = _refreshing

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
                            syncClientApi,
                            signalProtocolStore,
                            syncRuntimeInfoDao,
                        )
                },
            )
            withContext(mainDispatcher) {
                realTimeSyncRuntimeInfos.addAll(syncRuntimeInfos)
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
                                    syncClientApi,
                                    signalProtocolStore,
                                    syncRuntimeInfoDao,
                                )
                            resolveSync(insertionSyncRuntimeInfo.appInstanceId, true)
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
                                resolveSync(changeSyncRuntimeInfo.appInstanceId, true)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            realTimeSyncRuntimeInfos.clear()
                            realTimeSyncRuntimeInfos.addAll(changes.list)
                        }
                    }
                    else -> {
                        // types other than UpdatedResults are not changes -- ignore them
                    }
                }
            }
        }
    }

    override fun resolveSyncs(force: Boolean) {
        internalSyncHandlers.values.forEach { syncHandler ->
            realTimeSyncScope.launch {
                syncHandler.resolveSync(force)
            }
        }
    }

    override fun resolveSync(
        id: String,
        force: Boolean,
    ) {
        internalSyncHandlers[id]?.let { syncHandler ->
            realTimeSyncScope.launch(CoroutineName("SyncManagerResolve")) {
                syncHandler.resolveSync(force)
            }
        }
    }

    override fun getSyncHandlers(): Map<String, SyncHandler> {
        return internalSyncHandlers
    }

    override suspend fun trustByToken(
        appInstanceId: String,
        token: Int,
    ) {
        internalSyncHandlers[appInstanceId]?.also {
            it.trustByToken(token)
            it.resolveSync(false)
        }
    }

    override fun refresh(force: Boolean) {
        _refreshing.value = true
        realTimeSyncScope.launch(CoroutineName("SyncManagerRefresh")) {
            logger.info { "start launch" }
            try {
                resolveSyncs(force)
            } catch (e: Exception) {
                logger.error(e) { "checkConnects error" }
            }
            delay(1000)
            logger.info { "set refreshing false" }
            _refreshing.value = false
        }
    }
}
