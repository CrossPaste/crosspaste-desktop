package com.crosspaste.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.app.AppInfo
import com.crosspaste.app.VersionCompatibilityChecker
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.realm.signal.SignalRealm
import com.crosspaste.realm.sync.ChangeType
import com.crosspaste.realm.sync.SyncRuntimeInfo
import com.crosspaste.realm.sync.SyncRuntimeInfoRealm
import com.crosspaste.realm.sync.SyncState
import com.crosspaste.realm.sync.createSyncRuntimeInfo
import com.crosspaste.signal.SessionBuilderFactory
import com.crosspaste.signal.SignalProcessorCache
import com.crosspaste.signal.SignalProtocolStoreInterface
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.PasteDialog
import com.crosspaste.ui.devices.DeviceVerifyView
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SyncManager(
    private val appInfo: AppInfo,
    private val checker: VersionCompatibilityChecker,
    private val dialogService: DialogService,
    private val telnetHelper: TelnetHelper,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncClientApi: SyncClientApi,
    private val sessionBuilderFactory: SessionBuilderFactory,
    private val signalProtocolStore: SignalProtocolStoreInterface,
    private val signalProcessorCache: SignalProcessorCache,
    private val syncRuntimeInfoRealm: SyncRuntimeInfoRealm,
    private val signalRealm: SignalRealm,
    lazyDeviceManager: Lazy<DeviceManager>,
) : ViewModel() {

    private val logger = KotlinLogging.logger {}

    private val tokenCache: TokenCache = TokenCache

    private val _realTimeSyncRuntimeInfos = MutableStateFlow<List<SyncRuntimeInfo>>(listOf())

    val realTimeSyncRuntimeInfos: StateFlow<List<SyncRuntimeInfo>> = _realTimeSyncRuntimeInfos.asStateFlow()

    private val ignoreVerifySet: MutableSet<String> = ConcurrentSet()

    private var internalSyncHandlers: MutableMap<String, SyncHandler> = ConcurrentMap()

    private val realTimeSyncScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val deviceManager: DeviceManager by lazyDeviceManager

    var refreshing by mutableStateOf(false)

    init {
        viewModelScope.launch {
            val syncRuntimeInfos = syncRuntimeInfoRealm.getAllSyncRuntimeInfos()
            internalSyncHandlers.putAll(
                syncRuntimeInfos.map { syncRuntimeInfo ->
                    syncRuntimeInfo.appInstanceId to createSyncHandler(syncRuntimeInfo)
                },
            )
            withContext(mainDispatcher) {
                _realTimeSyncRuntimeInfos.value = syncRuntimeInfos
                refreshWaitToVerifySyncRuntimeInfo()
                deviceManager.refresh()
            }
            withContext(ioDispatcher) {
                resolveSyncs()
            }
            val syncRuntimeInfosFlow = syncRuntimeInfos.asFlow()
            syncRuntimeInfosFlow.collect { changes: ResultsChange<SyncRuntimeInfo> ->
                when (changes) {
                    is UpdatedResults -> {
                        for (deletion in changes.deletions) {
                            val deletionSyncRuntimeInfo = realTimeSyncRuntimeInfos.value[deletion]
                            internalSyncHandlers.remove(deletionSyncRuntimeInfo.appInstanceId)
                                ?.clearContext()
                        }

                        for (insertion in changes.insertions) {
                            val insertionSyncRuntimeInfo = changes.list[insertion]
                            internalSyncHandlers[insertionSyncRuntimeInfo.appInstanceId] =
                                createSyncHandler(insertionSyncRuntimeInfo)
                        }

                        // When the synchronization parameters change,
                        // we will force resolve, so we do not need to process it redundantly here
                        // for (change in changes.changes) { }

                        withContext(mainDispatcher) {
                            _realTimeSyncRuntimeInfos.value = changes.list
                            refreshWaitToVerifySyncRuntimeInfo()
                            if (changes.insertions.isNotEmpty() || changes.deletions.isNotEmpty()) {
                                deviceManager.refresh()
                            }
                        }
                    }
                    else -> {
                        // types other than UpdatedResults are not changes -- ignore them
                    }
                }
            }
        }
    }

    private fun createSyncHandler(syncRuntimeInfo: SyncRuntimeInfo): SyncHandler {
        return SyncHandler(
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
            syncRuntimeInfoRealm,
            signalRealm,
        )
    }

    private fun refreshWaitToVerifySyncRuntimeInfo() {
        realTimeSyncRuntimeInfos.value
            .filter { !ignoreVerifySet.contains(it.appInstanceId) }
            .firstOrNull { it.connectState == SyncState.UNVERIFIED }
            ?.let { info ->
                dialogService.pushDialog(
                    PasteDialog(
                        key = info.deviceId,
                        title = "do_you_trust_this_device?",
                        width = 320.dp,
                    ) {
                        DeviceVerifyView(info)
                    },
                )
            }
    }

    fun ignoreVerify(appInstanceId: String) {
        ignoreVerifySet.add(appInstanceId)
        refreshWaitToVerifySyncRuntimeInfo()
    }

    fun toVerify(appInstanceId: String) {
        ignoreVerifySet.remove(appInstanceId)
        refreshWaitToVerifySyncRuntimeInfo()
    }

    suspend fun resolveSyncs() =
        coroutineScope {
            internalSyncHandlers.values.map { syncHandler ->
                async {
                    doResolveSync(syncHandler)
                }
            }
        }

    suspend fun resolveSync(id: String) =
        coroutineScope {
            internalSyncHandlers[id]?.let { syncHandler ->
                async {
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
            syncRuntimeInfoRealm.deleteSyncRuntimeInfo(id)
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
            if (syncRuntimeInfoRealm.insertOrUpdate(newSyncRuntimeInfo) == ChangeType.NO_CHANGE) {
                internalSyncHandlers[syncInfo.appInfo.appInstanceId]?.tryDirectUpdateConnected()
            }
        }
    }

    fun refresh(ids: List<String> = listOf()) {
        refreshing = true
        realTimeSyncScope.launch(CoroutineName("SyncManagerRefresh")) {
            logger.info { "start launch" }
            try {
                if (ids.isEmpty()) {
                    resolveSyncs()
                } else {
                    ids.forEach { id ->
                        resolveSync(id)
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "checkConnects error" }
            }
            delay(1000)
            logger.info { "set refreshing false" }
            refreshing = false
        }
    }
}
