package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.app.VersionCompatibilityChecker
import com.crosspaste.dao.signal.SignalDao
import com.crosspaste.dao.sync.HostInfo
import com.crosspaste.dao.sync.SyncRuntimeInfo
import com.crosspaste.dao.sync.SyncRuntimeInfoDao
import com.crosspaste.dao.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.signal.PreKeyBundleInterface
import com.crosspaste.signal.SessionBuilderFactory
import com.crosspaste.signal.SignalAddress
import com.crosspaste.signal.SignalProcessorCache
import com.crosspaste.signal.SignalProtocolStoreInterface
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getNetUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val tokenCache: TokenCache,
    private val telnetHelper: TelnetHelper,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncClientApi: SyncClientApi,
    private val sessionBuilderFactory: SessionBuilderFactory,
    private val signalProtocolStore: SignalProtocolStoreInterface,
    private val signalProcessorCache: SignalProcessorCache,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val signalDao: SignalDao,
    scope: CoroutineScope,
) {

    private val logger = KotlinLogging.logger {}

    private val netUtils = getNetUtils()

    @Volatile
    var compatibility: Boolean =
        !checker.hasApiCompatibilityChangesBetween(
            appInfo.appVersion,
            syncRuntimeInfo.appVersion,
        )

    private val appInstanceId: String = syncRuntimeInfo.appInstanceId

    val signalProcessor = signalProcessorCache.getSignalMessageProcessor(syncRuntimeInfo.appInstanceId)

    var recommendedRefreshTime: Long = 0L

    private var failTime = 0

    private val job: Job

    private val mutex: Mutex = Mutex()

    init {
        job =
            scope.launch {
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

        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            tokenCache.getToken(syncRuntimeInfo.appInstanceId)?.let { token ->
                trustByToken(token)
            }
            resolveConnecting()
        }

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
        syncRuntimeInfoDao.suspendUpdate(syncRuntimeInfo) {
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
            if (isExistSession()) {
                if (heartbeat(host, syncRuntimeInfo.port, syncRuntimeInfo.appInstanceId)) {
                    return@resolveConnecting
                }
                if (syncRuntimeInfo.connectState == SyncState.UNMATCHED) {
                    logger.info { "heartbeat fail and connectState is unmatched, create new session $host ${syncRuntimeInfo.port}" }
                    createSession(host, syncRuntimeInfo.port, syncRuntimeInfo.appInstanceId)
                } else {
                    logger.info { "heartbeat fail $host ${syncRuntimeInfo.port}" }
                }
            } else {
                logger.info { "not exist session to create session $host ${syncRuntimeInfo.port}" }
                createSession(host, syncRuntimeInfo.port, syncRuntimeInfo.appInstanceId)
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
    ): Boolean {
        val result =
            syncClientApi.heartbeat(
                getCurrentSyncInfo(),
                signalProcessor,
                targetAppInstanceId,
            ) { urlBuilder ->
                buildUrl(urlBuilder, host, port)
            }

        when (result) {
            is SuccessResult -> {
                update {
                    this.connectState = SyncState.CONNECTED
                    this.modifyTime = RealmInstant.now()
                }
                return true
            }

            is FailureResult -> {
                if (result.exception.getErrorCode().code ==
                    StandardErrorCode.SYNC_NOT_MATCH_APP_INSTANCE_ID.getCode()
                ) {
                    logger.info { "heartbeat return fail state to disconnect $host $port" }
                    update {
                        this.connectHostAddress = null
                        this.connectState = SyncState.DISCONNECTED
                        this.modifyTime = RealmInstant.now()
                    }
                } else {
                    logger.info { "exchangeSyncInfo return fail state to unmatched $host $port" }
                    update {
                        this.connectState = SyncState.UNMATCHED
                        this.modifyTime = RealmInstant.now()
                    }
                }
                return false
            }

            else -> {
                logger.info { "exchangeSyncInfo connect fail state to unmatched $host $port" }
                update {
                    this.connectState = SyncState.DISCONNECTED
                    this.modifyTime = RealmInstant.now()
                }
                return false
            }
        }
    }

    private suspend fun createSession(
        host: String,
        port: Int,
        targetAppInstanceId: String,
    ) {
        val result =
            syncClientApi.isTrust(targetAppInstanceId) { urlBuilder ->
                buildUrl(urlBuilder, host, port)
            }

        when (result) {
            is SuccessResult -> {
                val preKeyBundleResult =
                    syncClientApi.getPreKeyBundle { urlBuilder ->
                        buildUrl(urlBuilder, host, port)
                    }

                when (preKeyBundleResult) {
                    is SuccessResult -> {
                        val preKeyBundle = preKeyBundleResult.getResult<PreKeyBundleInterface>()
                        val signalAddress = SignalAddress(appInstanceId, 1)
                        try {
                            val sessionBuilder = sessionBuilderFactory.createSessionBuilder(signalAddress)
                            signalProtocolStore.saveIdentity(signalAddress, preKeyBundle)
                            sessionBuilder.process(preKeyBundle)
                        } catch (e: Exception) {
                            logger.warn(e) { "createSession exchangeSyncInfo fail" }
                            update {
                                this.connectState = SyncState.DISCONNECTED
                                this.modifyTime = RealmInstant.now()
                            }
                            return
                        }
                        val resultCreateSession =
                            syncClientApi.createSession(
                                getCurrentSyncInfo(),
                                signalProcessor,
                            ) { urlBuilder ->
                                buildUrl(urlBuilder, host, port)
                            }
                        when (resultCreateSession) {
                            is SuccessResult -> {
                                update {
                                    this.connectState = SyncState.CONNECTED
                                    this.modifyTime = RealmInstant.now()
                                }
                            }

                            is FailureResult -> {
                                logger.info { "createSession return fail state to unmatched $host $port" }
                                update {
                                    this.connectState = SyncState.UNMATCHED
                                    this.modifyTime = RealmInstant.now()
                                }
                            }

                            else -> {
                                logger.info { "createSession connect fail state to unmatched $host $port" }
                                update {
                                    this.connectState = SyncState.DISCONNECTED
                                    this.modifyTime = RealmInstant.now()
                                }
                            }
                        }
                    }
                }
            }
            is FailureResult -> {
                if (result.exception.getErrorCode().code ==
                    StandardErrorCode.SYNC_NOT_MATCH_APP_INSTANCE_ID.getCode()
                ) {
                    logger.info { "heartbeat return fail state to disconnect $host $port" }
                    update {
                        this.connectHostAddress = null
                        this.connectState = SyncState.DISCONNECTED
                        this.modifyTime = RealmInstant.now()
                    }
                } else {
                    logger.info { "connect state to unverified $host $port" }
                    update {
                        this.connectState = SyncState.UNVERIFIED
                        this.modifyTime = RealmInstant.now()
                        logger.info { "createSession ${syncRuntimeInfo.platformName} UNVERIFIED" }
                    }
                }
            }
            else -> {
                logger.info { "connect state to disconnect $host $port" }
                update {
                    this.connectState = SyncState.DISCONNECTED
                    this.modifyTime = RealmInstant.now()
                }
            }
        }
    }

    suspend fun trustByToken(token: Int) {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                syncClientApi.trust(token) { urlBuilder ->
                    buildUrl(urlBuilder, host, syncRuntimeInfo.port)
                }
            }
        }
    }

    suspend fun showToken() {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val result =
                    syncClientApi.showToken { urlBuilder ->
                        buildUrl(urlBuilder, host, syncRuntimeInfo.port)
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
                syncClientApi.notifyExit { urlBuilder ->
                    buildUrl(urlBuilder, host, syncRuntimeInfo.port)
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

    private fun isExistSession(): Boolean {
        return signalProtocolStore.existSession(SignalAddress(appInstanceId, 1))
    }

    suspend fun clearContext() {
        signalProcessorCache.removeSignalMessageProcessor(syncRuntimeInfo.appInstanceId)
        signalDao.deleteSession(syncRuntimeInfo.appInstanceId)
        signalDao.deleteIdentity(syncRuntimeInfo.appInstanceId)
        syncRuntimeInfo.connectHostAddress?.let { host ->
            syncClientApi.notifyRemove { urlBuilder ->
                buildUrl(urlBuilder, host, syncRuntimeInfo.port)
            }
        }
        job.cancel()
    }
}
