package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.app.VersionCompatibilityChecker
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.realm.sync.HostInfo
import com.crosspaste.realm.sync.SyncRuntimeInfo
import com.crosspaste.realm.sync.SyncRuntimeInfoRealm
import com.crosspaste.realm.sync.SyncState
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getNetUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

class SyncHandler(
    private val appInfo: AppInfo,
    var syncRuntimeInfo: SyncRuntimeInfo,
    private val checker: VersionCompatibilityChecker,
    private val telnetHelper: TelnetHelper,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncClientApi: SyncClientApi,
    private val secureStore: SecureStore,
    private val syncRuntimeInfoRealm: SyncRuntimeInfoRealm,
    private val tokenCache: TokenCache,
) {

    private val logger = KotlinLogging.logger {}

    private val netUtils = getNetUtils()

    private val syncHandlerScope = CoroutineScope(ioDispatcher + SupervisorJob())

    @Volatile
    var compatibility: Boolean =
        !checker.hasApiCompatibilityChangesBetween(
            appInfo.appVersion,
            syncRuntimeInfo.appVersion,
        )

    private var recommendedRefreshTime: Long = 0L

    private var failTime = 0

    private val job: Job

    private val mutex: Mutex = Mutex()

    init {
        job =
            syncHandlerScope.launch {
                while (isActive) {
                    try {
                        pollingResolve()
                    } catch (e: Exception) {
                        if (e !is CancellationException) {
                            logger.error(e) { "resolve error" }
                        }
                    }
                }
            }
    }

    private suspend fun pollingResolve() {
        mutex.withLock {
            if (recommendedRefreshTime > Clock.System.now().toEpochMilliseconds()) {
                return@withLock
            }
            if (syncRuntimeInfo.connectState == SyncState.DISCONNECTED ||
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

    private fun getCurrentSyncInfo(): SyncInfo {
        val hostInfoFilter: (HostInfo) -> Boolean =
            syncRuntimeInfo.connectHostAddress?.let { hostAddress ->
                syncRuntimeInfo.connectNetworkPrefixLength?.let { networkPrefixLength ->
                    { hostInfo ->
                        networkPrefixLength == hostInfo.networkPrefixLength &&
                            netUtils.hostPreFixMatch(hostAddress, hostInfo.hostAddress, networkPrefixLength)
                    }
                } ?: { true }
            } ?: { true }

        return syncInfoFactory.createSyncInfo(hostInfoFilter)
    }

    private suspend fun waitNext() {
        if (recommendedRefreshTime <= Clock.System.now().toEpochMilliseconds()) {
            mutex.withLock {
                recommendedRefreshTime = computeRefreshTime()
            }
        }

        do {
            // if recommendedRefreshTime is updated, then we continue to wait for the new time
            val waitTime = recommendedRefreshTime - Clock.System.now().toEpochMilliseconds()
            delay(waitTime)
        } while (waitTime > 0)
    }

    private fun computeRefreshTime(): Long {
        var delayTime = 60000L // wait 1 min by default
        if (failTime > 0) {
            val power = min(11, failTime)
            delayTime = 1000 + min(20L * (1L shl power), 59000L)
        }
        return Clock.System.now().toEpochMilliseconds() + delayTime
    }

    suspend fun getConnectHostAddress(): String? {
        syncRuntimeInfo.connectHostAddress?.let {
            return it
        } ?: run {
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
            return syncRuntimeInfo.connectHostAddress
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

    suspend fun forceResolve() {
        mutex.withLock {
            doForceResolve()
        }
    }

    suspend fun update(block: SyncRuntimeInfo.() -> Unit): SyncRuntimeInfo? {
        syncRuntimeInfoRealm.suspendUpdate(syncRuntimeInfo) {
            block()
        }?. let {
            this.syncRuntimeInfo = it
            this.compatibility =
                !checker.hasApiCompatibilityChangesBetween(
                    appInfo.appVersion,
                    it.appVersion,
                )
            return it
        } ?: run {
            return null
        }
    }

    suspend fun tryDirectUpdateConnected() {
        mutex.withLock {
            telnetHelper.switchHost(syncRuntimeInfo.hostInfoList, syncRuntimeInfo.port)?.let { hostInfo ->
                update {
                    this.connectState = SyncState.CONNECTED
                    this.connectHostAddress = hostInfo.hostAddress
                    this.connectNetworkPrefixLength = hostInfo.networkPrefixLength
                    this.modifyTime = RealmInstant.now()
                }
                failTime = 0
                recommendedRefreshTime = computeRefreshTime()
            } ?: run {
                update {
                    this.connectState = SyncState.DISCONNECTED
                    this.modifyTime = RealmInstant.now()
                }
                failTime++
                recommendedRefreshTime = computeRefreshTime()
            }
        }
    }

    private suspend fun resolveDisconnected() {
        telnetHelper.switchHost(syncRuntimeInfo.hostInfoList, syncRuntimeInfo.port)?.let { hostInfo ->
            logger.info { "$hostInfo to connecting" }
            update {
                this.connectHostAddress = hostInfo.hostAddress
                this.connectNetworkPrefixLength = hostInfo.networkPrefixLength
                this.connectState = SyncState.CONNECTING
                this.modifyTime = RealmInstant.now()
            }
        } ?: run {
            logger.info { "${syncRuntimeInfo.platformName} to disconnected" }
            update {
                this.connectState = SyncState.DISCONNECTED
                this.modifyTime = RealmInstant.now()
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
                        update {
                            this.connectState = SyncState.CONNECTED
                            this.modifyTime = RealmInstant.now()
                        }
                        return@resolveConnecting
                    }
                    SyncState.UNMATCHED -> {
                        logger.info { "heartbeat fail and connectState is unmatched, need to re verify $host ${syncRuntimeInfo.port}" }
                        secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId)
                        tryUseTokenCache(host, syncRuntimeInfo.port)
                    }

                    else -> {
                        update {
                            this.connectState = SyncState.DISCONNECTED
                            this.modifyTime = RealmInstant.now()
                        }
                    }
                }
            } else {
                logger.info { "not exist identity, need to verify $host ${syncRuntimeInfo.port}" }
                tryUseTokenCache(host, syncRuntimeInfo.port)
            }
        } ?: run {
            logger.info { "${syncRuntimeInfo.platformName} to disconnected" }
            update {
                this.connectState = SyncState.DISCONNECTED
                this.modifyTime = RealmInstant.now()
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
                getCurrentSyncInfo(),
                targetAppInstanceId,
            ) {
                buildUrl(host, port)
            }

        when (result) {
            is SuccessResult -> {
                return SyncState.CONNECTED
            }

            is FailureResult -> {
                if (result.exception.getErrorCode().code ==
                    StandardErrorCode.SYNC_NOT_MATCH_APP_INSTANCE_ID.getCode()
                ) {
                    logger.info { "heartbeat return fail state to disconnect $host $port" }
                    return SyncState.DISCONNECTED
                } else {
                    logger.info { "exchangeSyncInfo return fail state to unmatched $host $port" }
                    return SyncState.UNMATCHED
                }
            }

            else -> {
                logger.info { "exchangeSyncInfo connect fail state to unmatched $host $port" }
                return SyncState.DISCONNECTED
            }
        }
    }

    private suspend fun tryUseTokenCache(host: String, port: Int) {
        if (trustByTokenCache()) {
            logger.info { "trustByTokenCache success $host $port" }
            update {
                this.connectState = SyncState.CONNECTED
                this.modifyTime = RealmInstant.now()
            }
        } else {
            update {
                this.connectState = SyncState.UNVERIFIED
                this.modifyTime = RealmInstant.now()
            }
        }
    }

    // use user input token to trust
    suspend fun trustByToken(token: Int) {
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
                val result = syncClientApi.trust(syncRuntimeInfo.appInstanceId, token) {
                    buildUrl(host, syncRuntimeInfo.port)
                }

                if (result is SuccessResult) {
                    return@trustByTokenCache true
                }
            }
        }
        return false
    }

    suspend fun showToken() {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val result =
                    syncClientApi.showToken {
                        buildUrl(host, syncRuntimeInfo.port)
                    }
                if (result !is SuccessResult) {
                    update {
                        this.connectState = SyncState.DISCONNECTED
                        this.modifyTime = RealmInstant.now()
                    }
                }
            }
        }
    }

    suspend fun notifyExit() {
        if (syncRuntimeInfo.connectState == SyncState.CONNECTED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                syncClientApi.notifyExit {
                    buildUrl(host, syncRuntimeInfo.port)
                }
            }
        }
        job.cancel()
    }

    suspend fun markExit() {
        logger.info { "markExit ${syncRuntimeInfo.appInstanceId}" }
        update {
            this.connectState = SyncState.DISCONNECTED
            this.modifyTime = RealmInstant.now()
        }
    }

    suspend fun clearContext() {
        secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId)
        syncRuntimeInfo.connectHostAddress?.let { host ->
            syncClientApi.notifyRemove {
                buildUrl(host, syncRuntimeInfo.port)
            }
        }
        job.cancel()
    }
}
