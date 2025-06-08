package com.crosspaste.sync

import com.crosspaste.app.RatingPromptManager
import com.crosspaste.db.sync.ChangeType
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.secure.SecureStore
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.PasteDialogFactory
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
    private val dialogService: DialogService,
    private val pasteDialogFactory: PasteDialogFactory,
    private val ratingPromptManager: RatingPromptManager,
    private val secureStore: SecureStore,
    private val syncClientApi: SyncClientApi,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val telnetHelper: TelnetHelper,
    private val tokenCache: TokenCache,
    lazyNearbyDeviceManager: Lazy<NearbyDeviceManager>,
) : SyncManager {

    private val logger = KotlinLogging.logger {}

    private val controlUtils = getControlUtils()

    private val _realTimeSyncRuntimeInfos = MutableStateFlow<List<SyncRuntimeInfo>>(listOf())

    override val realTimeSyncRuntimeInfos: StateFlow<List<SyncRuntimeInfo>> = _realTimeSyncRuntimeInfos.asStateFlow()

    private val _ignoreVerifySet = MutableStateFlow<Set<String>>(setOf())

    private val ignoreVerifySet: StateFlow<Set<String>> = _ignoreVerifySet.asStateFlow()

    private var internalSyncHandlers: MutableMap<String, SyncHandler> = ConcurrentMap()

    override val realTimeSyncScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val nearbyDeviceManager: NearbyDeviceManager by lazyNearbyDeviceManager

    private var started = false

    private var syncRuntimeInfosJob: Job? = null
    private var pasteDialogJob: Job? = null

    override fun start() {
        if (started) return
        started = true

        realTimeSyncScope.launch {
            val syncRuntimeInfos = syncRuntimeInfoDao.getAllSyncRuntimeInfos()
            internalSyncHandlers.putAll(
                syncRuntimeInfos.map { syncRuntimeInfo ->
                    syncRuntimeInfo.appInstanceId to createSyncHandler(syncRuntimeInfo)
                },
            )
            _realTimeSyncRuntimeInfos.value = syncRuntimeInfos
            nearbyDeviceManager.updateSyncManager()
            this@GeneralSyncManager.refresh()
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
                        internalSyncHandlers.remove(appInstanceId)?.clearContext()
                    }

                    newSet.forEach { appInstanceId ->
                        internalSyncHandlers[appInstanceId] = createSyncHandler(list.first { it.appInstanceId == appInstanceId })
                    }

                    withContext(mainDispatcher) {
                        _realTimeSyncRuntimeInfos.value = list
                        _ignoreVerifySet.update { set ->
                            set.filter {
                                it in currentAppInstanceIdSet
                            }.toSet()
                        }
                        if (newSet.isNotEmpty() || deleteSet.isNotEmpty()) {
                            nearbyDeviceManager.refreshSyncManager()
                        }
                    }

                    _realTimeSyncRuntimeInfos.value = list
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
            }
                .map { (syncInfos, ignoreSet) ->
                    syncInfos
                        .filter { !ignoreSet.contains(it.appInstanceId) }
                        .firstOrNull { it.connectState == SyncState.UNVERIFIED }
                }
                .filterNotNull()
                .onEach { info ->
                    val dialog =
                        pasteDialogFactory.createDialog(
                            key = info.deviceId,
                            title = "do_you_trust_this_device?",
                            onDismissRequest = { dialogService.popDialog() },
                        ) {
                            DeviceVerifyView(syncRuntimeInfo = info)
                        }
                    dialogService.pushDialog(dialog)
                }
                .launchIn(realTimeSyncScope)
    }

    override fun createSyncHandler(syncRuntimeInfo: SyncRuntimeInfo): SyncHandler {
        return GeneralSyncHandler(
            syncRuntimeInfo,
            ratingPromptManager,
            secureStore,
            syncClientApi,
            syncInfoFactory,
            syncRuntimeInfoDao,
            telnetHelper,
            tokenCache,
        )
    }

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
                        getSyncHandlers().values
                            .map {
                                async {
                                    doResolveSync(it)
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
                        ids.mapNotNull { getSyncHandler(it) }
                            .map {
                                async {
                                    doResolveSync(it)
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

    private suspend fun doResolveSync(syncHandler: SyncHandler) {
        runCatching {
            syncHandler.forceResolve()
        }.onFailure { e ->
            logger.error(e) { "resolve sync error" }
        }
    }

    override fun getSyncHandlers(): Map<String, SyncHandler> {
        return internalSyncHandlers
    }

    override fun removeSyncHandler(id: String) {
        realTimeSyncScope.launch(CoroutineName("RemoveSyncHandler")) {
            syncRuntimeInfoDao.deleteSyncRuntimeInfo(id)
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

    override fun updateSyncInfo(
        syncInfo: SyncInfo,
        refresh: Boolean,
    ) {
        realTimeSyncScope.launch(CoroutineName("UpdateSyncInfo")) {
            val (changeType, syncRuntimeInfo) = syncRuntimeInfoDao.insertOrUpdateSyncInfo(syncInfo)

            if (changeType != ChangeType.NO_CHANGE &&
                changeType != ChangeType.NEW_INSTANCE
            ) {
                getSyncHandler(syncInfo.appInfo.appInstanceId)
                    ?.setCurrentSyncRuntimeInfo(syncRuntimeInfo)
            }

            if (refresh) {
                if (changeType == ChangeType.NO_CHANGE ||
                    changeType == ChangeType.INFO_CHANGE
                ) {
                    getSyncHandler(syncInfo.appInfo.appInstanceId)?.tryDirectUpdateConnected()
                } else if (changeType == ChangeType.NET_CHANGE) {
                    refresh(listOf(syncRuntimeInfo.appInstanceId))
                }
            }
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
