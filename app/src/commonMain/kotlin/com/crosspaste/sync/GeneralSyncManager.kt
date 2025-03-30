package com.crosspaste.sync

import androidx.compose.ui.graphics.Color
import com.crosspaste.db.sync.ChangeType
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfo.Companion.createSyncRuntimeInfo
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
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    private val pasteDialogFactory: PasteDialogFactory,
    private val telnetHelper: TelnetHelper,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncClientApi: SyncClientApi,
    private val secureStore: SecureStore,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val tokenCache: TokenCache,
    lazyNearbyDeviceManager: Lazy<NearbyDeviceManager>,
) : SyncManager {

    private val logger = KotlinLogging.logger {}

    private val _realTimeSyncRuntimeInfos = MutableStateFlow<List<SyncRuntimeInfo>>(listOf())

    override val realTimeSyncRuntimeInfos: StateFlow<List<SyncRuntimeInfo>> = _realTimeSyncRuntimeInfos.asStateFlow()

    private val _ignoreVerifySet = MutableStateFlow<Set<String>>(setOf())

    private val ignoreVerifySet: StateFlow<Set<String>> = _ignoreVerifySet.asStateFlow()

    private var internalSyncHandlers: MutableMap<String, SyncHandler> = ConcurrentMap()

    override val realTimeSyncScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val nearbyDeviceManager: NearbyDeviceManager by lazyNearbyDeviceManager

    init {
        realTimeSyncScope.launch {
            val syncRuntimeInfos = syncRuntimeInfoDao.getAllSyncRuntimeInfos()
            internalSyncHandlers.putAll(
                syncRuntimeInfos.map { syncRuntimeInfo ->
                    syncRuntimeInfo.appInstanceId to createSyncHandler(syncRuntimeInfo)
                },
            )
            withContext(mainDispatcher) {
                _realTimeSyncRuntimeInfos.value = syncRuntimeInfos
                nearbyDeviceManager.refresh()
            }
            withContext(ioDispatcher) {
                resolveSyncs()
            }
            collectSyncRuntimeInfosFlow()
        }
        collectPasteDialog()
    }

    private suspend fun collectSyncRuntimeInfosFlow() {
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
                    nearbyDeviceManager.refresh()
                }
            }

            _realTimeSyncRuntimeInfos.value = list
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
                    pasteDialogFactory.createDialog(
                        key = info.deviceId,
                        title = "do_you_trust_this_device?",
                        onDismissRequest = { dialogService.popDialog() },
                    ) {
                        DeviceVerifyView(
                            syncRuntimeInfo = info,
                            background = Color.Transparent,
                        )
                    }
                dialogService.pushDialog(dialog)
            }
            .launchIn(realTimeSyncScope)
    }

    override fun createSyncHandler(syncRuntimeInfo: SyncRuntimeInfo): SyncHandler {
        return GeneralSyncHandler(
            syncRuntimeInfo,
            telnetHelper,
            syncInfoFactory,
            syncClientApi,
            secureStore,
            syncRuntimeInfoDao,
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

    override fun updateSyncInfo(syncInfo: SyncInfo) {
        realTimeSyncScope.launch(CoroutineName("UpdateSyncInfo")) {
            val newSyncRuntimeInfo = createSyncRuntimeInfo(syncInfo)
            if (syncRuntimeInfoDao.insertOrUpdateSyncRuntimeInfo(newSyncRuntimeInfo) ==
                ChangeType.NO_CHANGE
            ) {
                getSyncHandler(syncInfo.appInfo.appInstanceId)?.tryDirectUpdateConnected()
            }
        }
    }

    override fun refresh(ids: List<String>) {
        realTimeSyncScope.launch(CoroutineName("SyncManagerRefresh")) {
            logger.info { "start launch" }
            runCatching {
                if (ids.isEmpty()) {
                    resolveSyncs()
                } else {
                    ids.forEach { id ->
                        resolveSync(id)
                    }
                }
            }.onFailure { e ->
                logger.error(e) { "checkConnects error" }
            }.apply {
                delay(1000)
                logger.info { "set refreshing false" }
            }
        }
    }
}
