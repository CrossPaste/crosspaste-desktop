package com.crosspaste.sync

import com.crosspaste.db.sync.ConnectInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.SyncApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.filter
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.pairing.v3.PairingCapabilityFlag
import com.crosspaste.pairing.v3.PairingV3
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class GeneralSyncManager(
    override val realTimeSyncScope: CoroutineScope = namedScope(ioDispatcher, "GeneralSyncManager"),
    private val pendingExchangeLedger: PendingExchangeLedger,
    private val syncResolver: SyncResolverApi,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val syncClientApi: SyncClientApi,
    private val wsSessionManager: WsSessionManager,
    private val pairingCapabilityFlag: PairingCapabilityFlag,
) : SyncManager {

    private val logger = KotlinLogging.logger {}

    private val _realTimeSyncRuntimeInfos = MutableStateFlow<List<SyncRuntimeInfo>>(listOf())

    override val realTimeSyncRuntimeInfos: StateFlow<List<SyncRuntimeInfo>> = _realTimeSyncRuntimeInfos.asStateFlow()

    private val _ignoreVerifySet = MutableStateFlow<Set<String>>(setOf())

    private val ignoreVerifySet: StateFlow<Set<String>> = _ignoreVerifySet.asStateFlow()

    override val unverifiedSyncRuntimeInfo =
        combine(
            realTimeSyncRuntimeInfos,
            ignoreVerifySet,
        ) { syncInfos, ignoreSet ->
            syncInfos to ignoreSet
        }.map { (syncInfos, ignoreSet) ->
            syncInfos
                .filter { !ignoreSet.contains(it.appInstanceId) }
                .firstOrNull { it.connectState == SyncState.UNVERIFIED }
        }.stateIn(
            scope = realTimeSyncScope,
            started = WhileSubscribed(5000),
            initialValue = null,
        )

    // Keep protocol capability out of SyncRuntimeInfo so its persisted and serialized schema stays stable.
    private val _pairingCredentialTypes = MutableStateFlow<Map<String, PairingCredentialType>>(emptyMap())

    override val pairingCredentialTypes: StateFlow<Map<String, PairingCredentialType>> =
        _pairingCredentialTypes.asStateFlow()

    private val internalSyncHandlers: MutableMap<String, SyncHandler> = ConcurrentMap()

    private val eventChannel = Channel<SyncEvent>(Channel.UNLIMITED)

    private val started = atomic(false)

    private var syncRuntimeInfosJob: Job? = null

    override suspend fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return

        wsSessionManager.setOnSessionClosed { appInstanceId ->
            internalSyncHandlers[appInstanceId]?.let { handler ->
                realTimeSyncScope.launch {
                    handler.forceResolve()
                }
            }
        }

        // Inbound WebSocket registration is a reachability edge the polling loop would
        // otherwise only notice on its next cycle (up to ~60s away with backoff). This
        // matters most for client-only peers (browser extensions): they initiate the
        // connection toward us, so without this hook the device sits visibly offline
        // until the poll flips it to CONNECTED.
        wsSessionManager.setOnSessionOpened { appInstanceId ->
            internalSyncHandlers[appInstanceId]?.let { handler ->
                realTimeSyncScope.launch {
                    handler.fastReconnect()
                }
            }
        }

        realTimeSyncScope.launch {
            for (event in eventChannel) {
                launch {
                    syncResolver.emitEvent(event)
                }
            }
        }

        startCollectingSyncRuntimeInfosFlow()
    }

    override suspend fun stop() {
        if (!started.compareAndSet(expect = true, update = false)) return

        notifyExit()

        wsSessionManager.closeAll()

        syncRuntimeInfosJob?.cancel()
        syncRuntimeInfosJob = null

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
                        internalSyncHandlers.remove(appInstanceId)?.cancelScope()
                        wsSessionManager.unregisterSession(appInstanceId)
                    }
                    if (deleteSet.isNotEmpty()) {
                        _pairingCredentialTypes.update { it - deleteSet }
                    }

                    newSet.forEach { appInstanceId ->
                        val handler =
                            createSyncHandler(list.first { it.appInstanceId == appInstanceId })
                        internalSyncHandlers[appInstanceId] = handler
                        // Closes the race with the onSessionOpened hook: a WS session
                        // registered before this map write found no handler and was
                        // dropped (typical for a fresh pairing — the peer opens its
                        // socket while trustSyncInfo's DB write is still in flight).
                        // Re-checking after the write guarantees one of the two sides
                        // always fires. In a narrow interleaving BOTH may fire; that
                        // yields one redundant ForceResolve, which is idempotent and
                        // serialized per device — deliberately not deduplicated.
                        if (wsSessionManager.isConnected(appInstanceId)) {
                            launch {
                                handler.fastReconnect()
                            }
                        }
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

    private suspend fun emitEvent(event: SyncEvent) {
        eventChannel.send(event)
    }

    override fun createSyncHandler(syncRuntimeInfo: SyncRuntimeInfo): SyncHandler =
        GeneralSyncHandler(syncRuntimeInfo, ::emitEvent)

    override fun ignoreVerify(appInstanceId: String) {
        _ignoreVerifySet.update { it + appInstanceId }
    }

    override fun toVerify(appInstanceId: String) {
        _ignoreVerifySet.update { it - appInstanceId }
    }

    override fun rememberPairingCredentialType(syncInfo: SyncInfo) {
        val credentialType = pairingCredentialType(syncInfo)
        _pairingCredentialTypes.update {
            it + (syncInfo.appInfo.appInstanceId to credentialType)
        }
    }

    override suspend fun refreshPairingCredentialType(appInstanceId: String): PairingCredentialRefreshResult {
        _pairingCredentialTypes.value[appInstanceId]?.let {
            return PairingCredentialRefreshResult.Resolved(it)
        }

        val syncRuntimeInfo =
            realTimeSyncRuntimeInfos.value.firstOrNull {
                it.appInstanceId == appInstanceId && it.connectState == SyncState.UNVERIFIED
            }
                ?: return PairingCredentialRefreshResult.DeviceUnavailable
        val host = syncRuntimeInfo.connectHostAddress ?: return PairingCredentialRefreshResult.RetryableFailure

        return try {
            val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
            val result = syncClientApi.syncInfo { buildUrl(hostAndPort) }
            if (result !is SuccessResult) {
                logger.warn {
                    "Failed to fetch pairing metadata for $appInstanceId: ${result::class.simpleName}"
                }
                PairingCredentialRefreshResult.RetryableFailure
            } else {
                val syncInfo = result.getResult<SyncInfo>()
                when {
                    syncInfo.appInfo.appInstanceId != appInstanceId -> {
                        logger.warn {
                            "Pairing metadata identity mismatch: expected $appInstanceId, " +
                                "received ${syncInfo.appInfo.appInstanceId}"
                        }
                        PairingCredentialRefreshResult.IdentityMismatch
                    }
                    realTimeSyncRuntimeInfos.value.none { it.appInstanceId == appInstanceId } -> {
                        PairingCredentialRefreshResult.DeviceUnavailable
                    }
                    else -> {
                        val credentialType = pairingCredentialType(syncInfo)
                        rememberPairingCredentialType(syncInfo)
                        if (realTimeSyncRuntimeInfos.value.none { it.appInstanceId == appInstanceId }) {
                            _pairingCredentialTypes.update { it - appInstanceId }
                            PairingCredentialRefreshResult.DeviceUnavailable
                        } else {
                            PairingCredentialRefreshResult.Resolved(credentialType)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to refresh pairing metadata for $appInstanceId" }
            PairingCredentialRefreshResult.RetryableFailure
        }
    }

    private fun pairingCredentialType(syncInfo: SyncInfo): PairingCredentialType =
        selectPairingCredentialType(
            localPairingVersion = pairingCapabilityFlag.advertisedPairingVersion,
            remotePairingVersion = syncInfo.appInfo.pairingVersion,
        )

    private fun resolveSyncs(callback: () -> Unit) {
        realTimeSyncScope.launch {
            try {
                val jobs =
                    getSyncHandlers()
                        .values
                        .map {
                            async {
                                it.forceResolve()
                            }
                        }
                jobs.awaitAll()
            } catch (e: Exception) {
                logger.error(e) { "Exception while resolving sync handlers" }
            } finally {
                withContext(mainDispatcher) {
                    callback()
                }
            }
        }
    }

    private fun resolveSyncs(
        ids: List<String>,
        callback: () -> Unit,
    ) {
        realTimeSyncScope.launch {
            try {
                val jobs =
                    ids
                        .mapNotNull { getSyncHandler(it) }
                        .map {
                            async {
                                it.forceResolve()
                            }
                        }
                jobs.awaitAll()
            } catch (e: Exception) {
                logger.error(e) { "Exception while resolving sync handlers for ids: $ids" }
            } finally {
                withContext(mainDispatcher) {
                    callback()
                }
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

    override fun trustByBearerToken(
        appInstanceId: String,
        token: QrBearerToken,
        callback: (Boolean) -> Unit,
    ) {
        internalSyncHandlers[appInstanceId]?.let { syncHandler ->
            realTimeSyncScope.launch {
                syncHandler.trustByBearerToken(token, callback)
            }
        } ?: callback(false)
    }

    override fun trustBySasCode(
        appInstanceId: String,
        code: SasCode,
        callback: (Boolean) -> Unit,
    ) {
        internalSyncHandlers[appInstanceId]?.let { syncHandler ->
            realTimeSyncScope.launch {
                syncHandler.trustBySasCode(code, callback)
            }
        } ?: callback(false)
    }

    override fun exchangeKeysForPairing(appInstanceId: String) {
        internalSyncHandlers[appInstanceId]?.let { syncHandler ->
            // Allocate before launching or entering the event channel. A dialog
            // disposed while the resolver is busy can now capture this exact
            // generation, and a cancel processed first prevents the queued
            // exchange from being sent.
            val generation = pendingExchangeLedger.nextGeneration()
            pendingExchangeLedger.record(appInstanceId, generation)
            realTimeSyncScope.launch {
                syncHandler.exchangeKeysForPairing(generation)
            }
        }
    }

    override fun cancelPairing(appInstanceId: String) {
        // Capture the generation of the exchange the closing dialog owns,
        // synchronously: Compose disposes the old dialog before a reopened one
        // fires its warm-up, so whatever the ledger holds right now is ours to
        // cancel. No record → nothing we own → no request at all.
        val generation = pendingExchangeLedger.current(appInstanceId) ?: return
        internalSyncHandlers[appInstanceId]?.let { syncHandler ->
            realTimeSyncScope.launch {
                syncHandler.cancelPairing(generation)
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
        rememberPairingCredentialType(syncInfo)
        realTimeSyncScope.launch {
            syncRuntimeInfoDao.insertOrUpdateSyncInfo(syncInfo)
        }
    }

    override fun trustSyncInfo(
        syncInfo: SyncInfo,
        host: String?,
    ) {
        rememberPairingCredentialType(syncInfo)
        realTimeSyncScope.launch {
            val connectInfo =
                host?.let { h ->
                    syncInfo.endpointInfo.hostInfoList.firstOrNull { it.filter(h) }?.let {
                        ConnectInfo(it.networkPrefixLength, it.hostAddress)
                    }
                }
            syncRuntimeInfoDao.insertOrUpdateSyncInfo(syncInfo, connectInfo)
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

internal fun selectPairingCredentialType(
    localPairingVersion: Int,
    remotePairingVersion: Int?,
): PairingCredentialType =
    when {
        localPairingVersion >= PairingV3.PROTOCOL_VERSION &&
            SyncApi.supportsPairingV3(remotePairingVersion) ->
            PairingCredentialType.V3_PIN

        SyncApi.supportsSASPairing(remotePairingVersion) ->
            PairingCredentialType.SAS_CODE

        else -> PairingCredentialType.QR_BEARER_TOKEN
    }
