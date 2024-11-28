package com.crosspaste.sync

import androidx.compose.ui.unit.dp
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.realm.sync.ChangeType
import com.crosspaste.realm.sync.SyncRuntimeInfo
import com.crosspaste.realm.sync.SyncRuntimeInfoRealm
import com.crosspaste.realm.sync.SyncState
import com.crosspaste.realm.sync.createSyncRuntimeInfo
import com.crosspaste.secure.SecureStore
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GeneralSyncManager(
    private val dialogService: DialogService,
    private val telnetHelper: TelnetHelper,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncClientApi: SyncClientApi,
    private val secureStore: SecureStore,
    private val syncRuntimeInfoRealm: SyncRuntimeInfoRealm,
    private val tokenCache: TokenCache,
    lazyDeviceManager: Lazy<DeviceManager>,
) : SyncManager {

    private val logger = KotlinLogging.logger {}

    private val _realTimeSyncRuntimeInfos = MutableStateFlow<List<SyncRuntimeInfo>>(listOf())

    override val realTimeSyncRuntimeInfos: StateFlow<List<SyncRuntimeInfo>> = _realTimeSyncRuntimeInfos.asStateFlow()

    private val _ignoreVerifySet = MutableStateFlow<Set<String>>(setOf())

    private val ignoreVerifySet: StateFlow<Set<String>> = _ignoreVerifySet.asStateFlow()

    private var internalSyncHandlers: MutableMap<String, SyncHandler> = ConcurrentMap()

    override val realTimeSyncScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val deviceManager: DeviceManager by lazyDeviceManager

    init {
        realTimeSyncScope.launch {
            val syncRuntimeInfos = syncRuntimeInfoRealm.getAllSyncRuntimeInfos()
            internalSyncHandlers.putAll(
                syncRuntimeInfos.map { syncRuntimeInfo ->
                    syncRuntimeInfo.appInstanceId to createSyncHandler(syncRuntimeInfo)
                },
            )
            withContext(mainDispatcher) {
                _realTimeSyncRuntimeInfos.value = syncRuntimeInfos
                deviceManager.refresh()
            }
            withContext(ioDispatcher) {
                resolveSyncs()
            }
            collectSyncRuntimeInfosFlow(syncRuntimeInfos.asFlow())
        }
        collectPasteDialog()
    }

    private suspend fun collectSyncRuntimeInfosFlow(syncRuntimeInfosFlow: Flow<ResultsChange<SyncRuntimeInfo>>) {
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
                        _ignoreVerifySet.update { set ->
                            set.filter {
                                it in changes.list.map { info -> info.appInstanceId }
                            }.toSet()
                        }
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

    private fun collectPasteDialog() {
        combine(
            realTimeSyncRuntimeInfos,
            ignoreVerifySet,
        ) { syncInfos, ignoreSet ->
            syncInfos to ignoreSet
        }
            .map { (syncInfos, ignoreSet) ->
                syncInfos
                    .filter { !ignoreSet.contains(it.appInstanceId) }
                    .firstOrNull { it.connectState == SyncState.UNVERIFIED }
            }
            .filterNotNull()
            .onEach { info ->
                val dialog =
                    PasteDialog(
                        key = info.deviceId,
                        title = "do_you_trust_this_device?",
                        width = 320.dp,
                    ) {
                        DeviceVerifyView(info)
                    }
                dialogService.pushDialog(dialog)
            }
            .launchIn(realTimeSyncScope)
    }

    private fun createSyncHandler(syncRuntimeInfo: SyncRuntimeInfo): SyncHandler {
        return SyncHandler(
            syncRuntimeInfo,
            telnetHelper,
            syncInfoFactory,
            syncClientApi,
            secureStore,
            syncRuntimeInfoRealm,
            tokenCache,
        )
    }

    override fun ignoreVerify(appInstanceId: String) {
        _ignoreVerifySet.update { it + appInstanceId }
    }

    override fun toVerify(appInstanceId: String) {
        _ignoreVerifySet.update { it - appInstanceId }
    }

    override suspend fun resolveSyncs() {
        coroutineScope {
            getSyncHandlers().values.forEach { syncHandler ->
                launch {
                    doResolveSync(syncHandler)
                }
            }
        }
    }

    override suspend fun resolveSync(id: String) {
        coroutineScope {
            getSyncHandler(id)?.let { syncHandler ->
                launch {
                    doResolveSync(syncHandler)
                }
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

    override fun getSyncHandlers(): Map<String, SyncHandler> {
        return internalSyncHandlers
    }

    override fun removeSyncHandler(id: String) {
        realTimeSyncScope.launch(CoroutineName("RemoveSyncHandler")) {
            syncRuntimeInfoRealm.deleteSyncRuntimeInfo(id)
        }
    }

    override fun trustByToken(
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

    override fun updateSyncInfo(syncInfo: SyncInfo) {
        realTimeSyncScope.launch(CoroutineName("UpdateSyncInfo")) {
            val newSyncRuntimeInfo = createSyncRuntimeInfo(syncInfo)
            if (syncRuntimeInfoRealm.insertOrUpdate(newSyncRuntimeInfo) == ChangeType.NO_CHANGE) {
                getSyncHandler(syncInfo.appInfo.appInstanceId)?.tryDirectUpdateConnected()
            }
        }
    }

    override fun refresh(ids: List<String>) {
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
        }
    }
}
