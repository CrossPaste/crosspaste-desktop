package com.crosspaste.sync

import com.crosspaste.app.RatingPromptManager
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.DecryptFail
import com.crosspaste.net.clientapi.EncryptFail
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.HostInfoFilter
import com.crosspaste.utils.HostInfoFilterImpl
import com.crosspaste.utils.NoFilter
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

class GeneralSyncHandler(
    private var syncRuntimeInfo: SyncRuntimeInfo,
    private val ratingPromptManager: RatingPromptManager,
    private val secureStore: SecureStore,
    private val syncClientApi: SyncClientApi,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val telnetHelper: TelnetHelper,
    private val tokenCache: TokenCache,
) : SyncHandler {

    private val logger = KotlinLogging.logger {}

    private val syncHandlerScope = CoroutineScope(ioDispatcher + SupervisorJob())

    override var versionRelation: VersionRelation = VersionRelation.EQUAL_TO

    private var recommendedRefreshTime: Long = 0L

    private var failTime = 0

    private val job: Job

    private val mutex: Mutex = Mutex()

    private var syncInfo: SyncInfo? = null

    init {
        job =
            syncHandlerScope.launch {
                while (isActive) {
                    runCatching {
                        pollingResolve()
                    }.onFailure { e ->
                        if (e !is CancellationException) {
                            logger.error(e) { "polling error" }
                        }
                    }
                }
            }
    }

    override fun getCurrentSyncRuntimeInfo(): SyncRuntimeInfo {
        return syncRuntimeInfo
    }

    override suspend fun setCurrentSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo) {
        return mutex.withLock {
            this.syncRuntimeInfo = syncRuntimeInfo
        }
    }

    private suspend fun pollingResolve() {
        mutex.withLock {
            if (recommendedRefreshTime > nowEpochMilliseconds()) {
                return@withLock
            }
            if (syncRuntimeInfo.connectState == SyncState.DISCONNECTED ||
                syncRuntimeInfo.connectState == SyncState.INCOMPATIBLE ||
                syncRuntimeInfo.connectHostAddress == null
            ) {
                resolveDisconnected()
                if (syncRuntimeInfo.connectState != SyncState.CONNECTING) {
                    failTime++
                    return@withLock
                }
            }

            resolveConnecting()

            if (syncRuntimeInfo.connectState != SyncState.CONNECTED) {
                failTime++
                return@withLock
            } else {
                failTime = 0
            }
        }
        waitNext()
    }

    private fun getNewSyncInfo(): SyncInfo? {
        val hostInfoFilter: HostInfoFilter =
            syncRuntimeInfo.connectHostAddress?.let { hostAddress ->
                syncRuntimeInfo.connectNetworkPrefixLength?.let { networkPrefixLength ->
                    HostInfoFilterImpl(hostAddress, networkPrefixLength)
                } ?: NoFilter
            } ?: NoFilter

        val currentSyncInfo = syncInfoFactory.createSyncInfo(hostInfoFilter)
        return if (syncInfo == null) {
            syncInfo = currentSyncInfo
            currentSyncInfo
        } else if (syncInfo != currentSyncInfo) {
            syncInfo = currentSyncInfo
            currentSyncInfo
        } else {
            null
        }
    }

    private suspend fun waitNext() {
        if (recommendedRefreshTime <= nowEpochMilliseconds()) {
            mutex.withLock {
                recommendedRefreshTime = computeRefreshTime()
            }
        }

        do {
            // if recommendedRefreshTime is updated, then we continue to wait for the new time
            val waitTime = recommendedRefreshTime - nowEpochMilliseconds()
            delay(waitTime)
        } while (waitTime > 0)
    }

    private fun computeRefreshTime(): Long {
        var delayTime = 60000L // wait 1 min by default
        if (failTime > 0) {
            val power = min(11, failTime)
            delayTime = 1000 + min(20L * (1L shl power), 59000L)
        }
        return nowEpochMilliseconds() + delayTime
    }

    override suspend fun getConnectHostAddress(): String? {
        return syncRuntimeInfo.connectHostAddress ?: run {
            mutex.withLock {
                // getConnectionHostAddress may be called by multiple threads
                // only one thread enters to solve the connection problem
                // when other threads enter after the solution is completed
                // we should check again whether the connection is resolved
                // and then decide whether to resolve the connection problem again
                if (syncRuntimeInfo.connectHostAddress != null) {
                    return syncRuntimeInfo.connectHostAddress
                } else {
                    doForceResolve()
                }
            }
            syncRuntimeInfo.connectHostAddress
        }
    }

    private suspend fun doForceResolve() {
        if (syncRuntimeInfo.connectState == SyncState.CONNECTED) {
            resolveConnecting()
        }

        if (syncRuntimeInfo.connectState == SyncState.CONNECTED) {
            failTime = 0
            recommendedRefreshTime = computeRefreshTime()
            return
        }

        resolveDisconnected()
        if (syncRuntimeInfo.connectState != SyncState.CONNECTING) {
            failTime++
            recommendedRefreshTime = computeRefreshTime()
            return
        }

        resolveConnecting()

        if (syncRuntimeInfo.connectState != SyncState.CONNECTED) {
            failTime++
            recommendedRefreshTime = computeRefreshTime()
        } else {
            failTime = 0
            recommendedRefreshTime = computeRefreshTime()
        }
    }

    override suspend fun forceResolve() {
        mutex.withLock {
            doForceResolve()
        }
    }

    override suspend fun updateSyncRuntimeInfo(doUpdate: (SyncRuntimeInfo) -> SyncRuntimeInfo): SyncRuntimeInfo? {
        return mutex.withLock {
            update(doUpdate)
        }
    }

    private fun update(doUpdate: (SyncRuntimeInfo) -> SyncRuntimeInfo): SyncRuntimeInfo? {
        val newSyncRuntimeInfo = doUpdate(syncRuntimeInfo)
        return syncRuntimeInfoDao.updateConnectInfo(newSyncRuntimeInfo) {
            syncRuntimeInfo = newSyncRuntimeInfo
        }?.let {
            newSyncRuntimeInfo
        }
    }

    override suspend fun tryDirectUpdateConnected() {
        mutex.withLock {
            telnetHelper.switchHost(syncRuntimeInfo.hostInfoList, syncRuntimeInfo.port)?.let { pair ->
                val (hostInfo, versionRelation) = pair
                logger.info { "$hostInfo to connecting, versionRelation: $versionRelation" }
                this.versionRelation = versionRelation
                update { syncRuntimeInfo ->
                    syncRuntimeInfo.copy(
                        connectHostAddress = hostInfo.hostAddress,
                        connectNetworkPrefixLength = hostInfo.networkPrefixLength,
                        connectState =
                            if (versionRelation == VersionRelation.EQUAL_TO) {
                                SyncState.CONNECTED
                            } else {
                                SyncState.INCOMPATIBLE
                            },
                        modifyTime = nowEpochMilliseconds(),
                    )
                }
                failTime = 0
                recommendedRefreshTime = computeRefreshTime()
            } ?: run {
                update { syncRuntimeInfo ->
                    syncRuntimeInfo.copy(
                        connectState = SyncState.DISCONNECTED,
                        modifyTime = nowEpochMilliseconds(),
                    )
                }
                failTime++
                recommendedRefreshTime = computeRefreshTime()
            }
        }
    }

    private suspend fun resolveDisconnected() {
        telnetHelper.switchHost(syncRuntimeInfo.hostInfoList, syncRuntimeInfo.port)?.let { pair ->
            val (hostInfo, versionRelation) = pair
            logger.info { "$hostInfo to connecting, versionRelation: $versionRelation" }
            this.versionRelation = versionRelation
            update { syncRuntimeInfo ->
                syncRuntimeInfo.copy(
                    connectHostAddress = hostInfo.hostAddress,
                    connectNetworkPrefixLength = hostInfo.networkPrefixLength,
                    connectState =
                        if (versionRelation == VersionRelation.EQUAL_TO) {
                            SyncState.CONNECTING
                        } else {
                            SyncState.INCOMPATIBLE
                        },
                    modifyTime = nowEpochMilliseconds(),
                )
            }
        } ?: run {
            logger.info { "${syncRuntimeInfo.appInstanceId} to disconnected" }
            update { syncRuntimeInfo ->
                syncRuntimeInfo.copy(
                    connectState = SyncState.DISCONNECTED,
                    modifyTime = nowEpochMilliseconds(),
                )
            }
        }
    }

    private suspend fun resolveConnecting() {
        syncRuntimeInfo.connectHostAddress?.let { host ->
            if (secureStore.existCryptPublicKey(syncRuntimeInfo.appInstanceId)) {
                val state = heartbeat(host, syncRuntimeInfo.port, syncRuntimeInfo.appInstanceId)

                when (state) {
                    SyncState.CONNECTED -> {
                        logger.info { "heartbeat success $host ${syncRuntimeInfo.port}" }
                        update { syncRuntimeInfo ->
                            syncRuntimeInfo.copy(
                                connectState = SyncState.CONNECTED,
                                modifyTime = nowEpochMilliseconds(),
                            )
                        }
                        // track significant action
                        ratingPromptManager.trackSignificantAction()
                        return@resolveConnecting
                    }
                    SyncState.UNMATCHED -> {
                        logger.info { "heartbeat fail and connectState is unmatched, need to re verify $host ${syncRuntimeInfo.port}" }
                        secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId)
                        tryUseTokenCache(host, syncRuntimeInfo.port)
                    }
                    SyncState.INCOMPATIBLE -> {
                        logger.info { "heartbeat success and connectState is incompatible $host ${syncRuntimeInfo.port}" }
                        update { syncRuntimeInfo ->
                            syncRuntimeInfo.copy(
                                connectState = SyncState.INCOMPATIBLE,
                                modifyTime = nowEpochMilliseconds(),
                            )
                        }
                    }

                    else -> {
                        update { syncRuntimeInfo ->
                            syncRuntimeInfo.copy(
                                connectState = SyncState.DISCONNECTED,
                                modifyTime = nowEpochMilliseconds(),
                            )
                        }
                    }
                }
            } else {
                logger.info { "not exist identity, need to verify $host ${syncRuntimeInfo.port}" }
                tryUseTokenCache(host, syncRuntimeInfo.port)
            }
        } ?: run {
            logger.info { "${syncRuntimeInfo.platform.name} to disconnected" }
            update { syncRuntimeInfo ->
                syncRuntimeInfo.copy(
                    connectState = SyncState.DISCONNECTED,
                    modifyTime = nowEpochMilliseconds(),
                )
            }
        }
    }

    private suspend fun heartbeat(
        host: String,
        port: Int,
        targetAppInstanceId: String,
    ): Int {
        val result =
            syncClientApi.heartbeat(
                getNewSyncInfo(),
                targetAppInstanceId,
            ) {
                buildUrl(host, port)
            }

        return when (result) {
            is SuccessResult -> {
                this.versionRelation = result.getResult()
                if (this.versionRelation == VersionRelation.EQUAL_TO) {
                    SyncState.CONNECTED
                } else {
                    SyncState.INCOMPATIBLE
                }
            }

            is FailureResult -> {
                val failErrorCode = result.exception.getErrorCode().code
                when (failErrorCode) {
                    StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID.getCode() -> {
                        logger.info { "heartbeat return fail state to disconnect $host $port" }
                        SyncState.DISCONNECTED
                    }
                    StandardErrorCode.DECRYPT_FAIL.getCode() -> {
                        logger.info { "exchangeSyncInfo return fail state to unmatched $host $port $failErrorCode" }
                        SyncState.UNMATCHED
                    }
                    else -> {
                        logger.info { "failErrorCode $failErrorCode $host $port" }
                        SyncState.DISCONNECTED
                    }
                }
            }

            is EncryptFail -> {
                logger.info { "exchangeSyncInfo encrypt fail $host $port" }
                SyncState.UNMATCHED
            }
            is DecryptFail -> {
                logger.info { "exchangeSyncInfo decrypt fail $host $port" }
                SyncState.UNMATCHED
            }

            else -> {
                logger.info { "exchangeSyncInfo connect fail state to disconnected $host $port" }
                SyncState.DISCONNECTED
            }
        }
    }

    private suspend fun tryUseTokenCache(
        host: String,
        port: Int,
    ) {
        if (trustByTokenCache()) {
            logger.info { "trustByTokenCache success $host $port" }
            update { syncRuntimeInfo ->
                syncRuntimeInfo.copy(
                    connectState = SyncState.CONNECTED,
                    modifyTime = nowEpochMilliseconds(),
                )
            }
            ratingPromptManager.trackSignificantAction()
        } else {
            syncRuntimeInfo.connectHostAddress?.let {
                telnetHelper.telnet(it, syncRuntimeInfo.port)?.let { versionRelation ->
                    this.versionRelation = versionRelation
                    if (versionRelation == VersionRelation.EQUAL_TO) {
                        logger.info { "telnet success $host $port" }
                        update { syncRuntimeInfo ->
                            syncRuntimeInfo.copy(
                                connectState = SyncState.UNVERIFIED,
                                modifyTime = nowEpochMilliseconds(),
                            )
                        }
                    } else {
                        logger.info { "telnet fail $host $port" }
                        update { syncRuntimeInfo ->
                            syncRuntimeInfo.copy(
                                connectState = SyncState.INCOMPATIBLE,
                                modifyTime = nowEpochMilliseconds(),
                            )
                        }
                    }
                    return@tryUseTokenCache
                }
            }
            update { syncRuntimeInfo ->
                syncRuntimeInfo.copy(
                    connectState = SyncState.DISCONNECTED,
                    modifyTime = nowEpochMilliseconds(),
                )
            }
        }
    }

    // use user input token to trust
    override suspend fun trustByToken(token: Int) {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                syncClientApi.trust(syncRuntimeInfo.appInstanceId, token) {
                    buildUrl(host, syncRuntimeInfo.port)
                }
            }
        }
    }

    // try to use camera to scan token to trust
    private suspend fun trustByTokenCache(): Boolean {
        tokenCache.getToken(syncRuntimeInfo.appInstanceId)?.let { token ->
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val result =
                    syncClientApi.trust(syncRuntimeInfo.appInstanceId, token) {
                        buildUrl(host, syncRuntimeInfo.port)
                    }

                if (result is SuccessResult) {
                    return@trustByTokenCache true
                }
            }
        }
        return false
    }

    override suspend fun showToken(syncRuntimeInfo: SyncRuntimeInfo) {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val result =
                    syncClientApi.showToken {
                        buildUrl(host, syncRuntimeInfo.port)
                    }
                if (result !is SuccessResult) {
                    update { syncRuntimeInfo ->
                        syncRuntimeInfo.copy(
                            connectState = SyncState.DISCONNECTED,
                            modifyTime = nowEpochMilliseconds(),
                        )
                    }
                }
            }
        }
    }

    override suspend fun notifyExit() {
        if (syncRuntimeInfo.connectState == SyncState.CONNECTED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                syncClientApi.notifyExit {
                    buildUrl(host, syncRuntimeInfo.port)
                }
            }
        }
        job.cancel()
    }

    override suspend fun markExit() {
        logger.info { "markExit ${syncRuntimeInfo.appInstanceId}" }
        update { syncRuntimeInfo ->
            syncRuntimeInfo.copy(
                connectState = SyncState.DISCONNECTED,
                modifyTime = nowEpochMilliseconds(),
            )
        }
    }

    override suspend fun clearContext() {
        secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId)
        syncRuntimeInfo.connectHostAddress?.let { host ->
            syncClientApi.notifyRemove {
                buildUrl(host, syncRuntimeInfo.port)
            }
        }
        job.cancel()
    }
}
