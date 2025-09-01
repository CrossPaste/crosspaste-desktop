package com.crosspaste.sync

import androidx.compose.runtime.remember
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.PasteDialogFactory
import com.crosspaste.ui.devices.DeviceScopeFactory
import com.crosspaste.ui.devices.DeviceVerifyView
import com.crosspaste.utils.getControlUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class GeneralSyncManager(
    private val deviceScopeFactory: DeviceScopeFactory,
    private val dialogService: DialogService,
    private val pasteDialogFactory: PasteDialogFactory,
    override val realTimeSyncScope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob()),
    private val syncResolver: SyncResolver,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
) : SyncManager {

    private val logger = KotlinLogging.logger {}

    private val controlUtils = getControlUtils()

    private val _realTimeSyncRuntimeInfos = MutableStateFlow<List<SyncRuntimeInfo>>(listOf())

    override val realTimeSyncRuntimeInfos: StateFlow<List<SyncRuntimeInfo>> = _realTimeSyncRuntimeInfos.asStateFlow()

    private val _ignoreVerifySet = MutableStateFlow<Set<String>>(setOf())

    private val ignoreVerifySet: StateFlow<Set<String>> = _ignoreVerifySet.asStateFlow()

    private val internalSyncHandlers: MutableMap<String, SyncHandler> = ConcurrentMap()

    private val eventBus = MutableSharedFlow<SyncEvent>()

    private var started = false

    private var syncRuntimeInfosJob: Job? = null
    private var pasteDialogJob: Job? = null

    override fun start() {
        if (started) return
        started = true

        realTimeSyncScope.launch {
            eventBus.collect { event ->
                syncResolver.emitEvent(event)
            }
        }

        realTimeSyncScope.launch {
            val syncRuntimeInfos = syncRuntimeInfoDao.getAllSyncRuntimeInfos()
            internalSyncHandlers.putAll(
                syncRuntimeInfos.map { syncRuntimeInfo ->
                    syncRuntimeInfo.appInstanceId to createSyncHandler(syncRuntimeInfo)
                },
            )
            _realTimeSyncRuntimeInfos.value = syncRuntimeInfos
            startCollectingSyncRuntimeInfosFlow()
        }
        startCollectingPasteDialog()
    }

    override fun stop() {
        if (!started) return
        started = false

        notifyExit()

        syncRuntimeInfosJob?.cancel()
        syncRuntimeInfosJob = null

        pasteDialogJob?.cancel()
        pasteDialogJob = null

        realTimeSyncScope.cancel()
    }

    private fun startCollectingSyncRuntimeInfosFlow() {
        syncRuntimeInfosJob =
            realTimeSyncScope.launch {
                syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow().collect { list ->
                    val currentAppInstanceIdSet = list.map { it.appInstanceId }.toSet()
                    val previousAppInstanceIdSet = realTimeSyncRuntimeInfos.value.map { it.appInstanceId }.toSet()
                    val deleteSet = previousAppInstanceIdSet - currentAppInstanceIdSet
                    val newSet = currentAppInstanceIdSet - previousAppInstanceIdSet

                    deleteSet.forEach { appInstanceId ->
                        internalSyncHandlers.remove(appInstanceId)
                    }

                    newSet.forEach { appInstanceId ->
                        internalSyncHandlers[appInstanceId] =
                            createSyncHandler(list.first { it.appInstanceId == appInstanceId })
                    }

                    list
                        .filter { it.appInstanceId !in newSet }
                        .forEach {
                            internalSyncHandlers[it.appInstanceId]?.updateSyncRuntimeInfo(it)
                        }

                    _realTimeSyncRuntimeInfos.value = list

                    _ignoreVerifySet.value =
                        _ignoreVerifySet.value
                            .filter {
                                it in currentAppInstanceIdSet
                            }.toSet()
                }
            }
    }

    private fun startCollectingPasteDialog() {
        pasteDialogJob =
            combine(
                realTimeSyncRuntimeInfos,
                ignoreVerifySet,
            ) { syncInfos, ignoreSet ->
                syncInfos to ignoreSet
            }.map { (syncInfos, ignoreSet) ->
                syncInfos
                    .filter { !ignoreSet.contains(it.appInstanceId) }
                    .firstOrNull { it.connectState == SyncState.UNVERIFIED }
            }.filterNotNull()
                .onEach { info ->
                    val dialog =
                        pasteDialogFactory.createDialog(
                            key = info.deviceId,
                            title = "do_you_trust_this_device?",
                            onDismissRequest = { dialogService.popDialog() },
                        ) {
                            val scope = remember(info) { deviceScopeFactory.createDeviceScope(info) }

                            scope.DeviceVerifyView()
                        }
                    dialogService.pushDialog(dialog)
                }.launchIn(realTimeSyncScope)
    }

    private suspend fun emitEvent(event: SyncEvent) {
        eventBus.emit(event)
    }

    override fun createSyncHandler(syncRuntimeInfo: SyncRuntimeInfo): SyncHandler =
        GeneralSyncHandler(syncRuntimeInfo, ::emitEvent)

    override fun ignoreVerify(appInstanceId: String) {
        _ignoreVerifySet.update { it + appInstanceId }
    }

    override fun toVerify(appInstanceId: String) {
        _ignoreVerifySet.update { it - appInstanceId }
    }

    private fun resolveSyncs(callback: () -> Unit) {
        realTimeSyncScope.launch {
            controlUtils.ensureMinExecutionTime(delayTime = 1000L) {
                supervisorScope {
                    val jobs =
                        getSyncHandlers()
                            .values
                            .map {
                                async {
                                    it.forceResolve()
                                }
                            }
                    jobs.awaitAll()
                }
            }

            withContext(mainDispatcher) {
                callback()
            }
        }
    }

    private fun resolveSyncs(
        ids: List<String>,
        callback: () -> Unit,
    ) {
        realTimeSyncScope.launch {
            controlUtils.ensureMinExecutionTime(delayTime = 1000L) {
                supervisorScope {
                    val jobs =
                        ids
                            .mapNotNull { getSyncHandler(it) }
                            .map {
                                async {
                                    it.forceResolve()
                                }
                            }
                    jobs.awaitAll()
                }
            }

            withContext(mainDispatcher) {
                callback()
            }
        }
    }

    override fun getSyncHandlers(): Map<String, SyncHandler> = internalSyncHandlers

    override fun removeSyncHandler(appInstanceId: String) {
        internalSyncHandlers[appInstanceId]?.let { syncHandler ->
            realTimeSyncScope.launch(CoroutineName("RemoveSyncHandler")) {
                syncHandler.removeDevice()
            }
        }
    }

    override fun trustByToken(
        appInstanceId: String,
        token: Int,
    ) {
        internalSyncHandlers[appInstanceId]?.let { syncHandler ->
            realTimeSyncScope.launch {
                syncHandler.trustByToken(token)
            }
        }
    }

    override fun updateAllowSend(
        appInstanceId: String,
        allowSend: Boolean,
    ) {
        internalSyncHandlers[appInstanceId]?.let { syncHandler ->
            realTimeSyncScope.launch {
                syncHandler.updateAllowSend(allowSend)
            }
        }
    }

    override fun updateAllowReceive(
        appInstanceId: String,
        allowReceive: Boolean,
    ) {
        internalSyncHandlers[appInstanceId]?.let { syncHandler ->
            realTimeSyncScope.launch {
                syncHandler.updateAllowReceive(allowReceive)
            }
        }
    }

    override fun updateNoteName(
        appInstanceId: String,
        noteName: String,
    ) {
        internalSyncHandlers[appInstanceId]?.let { syncHandler ->
            realTimeSyncScope.launch {
                syncHandler.updateNoteName(noteName)
            }
        }
    }

    override fun updateSyncInfo(syncInfo: SyncInfo) {
        realTimeSyncScope.launch(CoroutineName("UpdateSyncInfo")) {
            emitEvent(SyncEvent.UpdateSyncInfo(syncInfo))
        }
    }

    override fun refresh(
        ids: List<String>,
        callback: () -> Unit,
    ) {
        runCatching {
            if (ids.isEmpty()) {
                resolveSyncs(callback)
            } else {
                resolveSyncs(ids, callback)
            }
        }.onFailure { e ->
            logger.error(e) { "checkConnects error" }
        }
    }
}
