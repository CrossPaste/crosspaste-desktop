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
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.signal.SignalMessageProcessorImpl
import com.crosspaste.signal.SignalProcessorCache
import com.crosspaste.utils.DesktopNetUtils.hostPreFixMatch
import com.crosspaste.utils.TelnetUtils
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.SignalProtocolStore
import kotlin.math.min

class DesktopSyncHandler(
    private val appInfo: AppInfo,
    override var syncRuntimeInfo: SyncRuntimeInfo,
    private val checker: VersionCompatibilityChecker,
    private val tokenCache: TokenCache,
    private val telnetUtils: TelnetUtils,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncClientApi: SyncClientApi,
    private val signalProtocolStore: SignalProtocolStore,
    private val signalProcessorCache: SignalProcessorCache,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val signalDao: SignalDao,
    scope: CoroutineScope,
) : SyncHandler {

    private val logger = KotlinLogging.logger {}

    @Volatile
    override var compatibility: Boolean =
        !checker.hasApiCompatibilityChangesBetween(
            appInfo.appVersion,
            syncRuntimeInfo.appVersion,
        )

    override val signalProcessor = signalProcessorCache.getSignalMessageProcessor(syncRuntimeInfo.appInstanceId)

    private val signalProtocolAddress = (signalProcessor as SignalMessageProcessorImpl).signalProtocolAddress

    override var recommendedRefreshTime: Long = 0L

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
                        logger.error(e) { "resolve error" }
                    }
                }
            }
    }

    private suspend fun pollingResolve() {
        mutex.withLock {
            if (recommendedRefreshTime > System.currentTimeMillis()) {
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
                            hostPreFixMatch(hostAddress, hostInfo.hostAddress, networkPrefixLength)
                    }
                } ?: { true }
            } ?: { true }

        return syncInfoFactory.createSyncInfo(hostInfoFilter)
    }

    private suspend fun waitNext() {
        if (recommendedRefreshTime <= System.currentTimeMillis()) {
            mutex.withLock {
                recommendedRefreshTime = computeRefreshTime()
            }
        }

        do {
            // if recommendedRefreshTime is updated, then we continue to wait for the new time
            val waitTime = recommendedRefreshTime - System.currentTimeMillis()
            delay(waitTime)
        } while (waitTime > 0)
    }

    private fun computeRefreshTime(): Long {
        var delayTime = 60000L // wait 1 min by default
        if (failTime > 0) {
            val power = min(11, failTime)
            delayTime = 1000 + min(20L * (1L shl power), 59000L)
        }
        return System.currentTimeMillis() + delayTime
    }

    override suspend fun getConnectHostAddress(): String? {
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

    override suspend fun forceResolve() {
        mutex.withLock {
            doForceResolve()
        }
    }

    override suspend fun update(block: SyncRuntimeInfo.() -> Unit): SyncRuntimeInfo? {
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

    override suspend fun tryDirectUpdateConnected() {
        mutex.withLock {
            telnetUtils.switchHost(syncRuntimeInfo.hostInfoList, syncRuntimeInfo.port)?.let { hostInfo ->
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
        telnetUtils.switchHost(syncRuntimeInfo.hostInfoList, syncRuntimeInfo.port)?.let { hostInfo ->
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
                        val preKeyBundle = preKeyBundleResult.getResult<PreKeyBundle>()
                        val sessionBuilder = createSessionBuilder()
                        try {
                            signalProtocolStore.saveIdentity(signalProtocolAddress, preKeyBundle.identityKey)
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

    override suspend fun trustByToken(token: Int) {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                syncClientApi.trust(token) { urlBuilder ->
                    buildUrl(urlBuilder, host, syncRuntimeInfo.port)
                }
            }
        }
    }

    override suspend fun showToken() {
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

    override suspend fun notifyExit() {
        if (syncRuntimeInfo.connectState == SyncState.CONNECTED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                syncClientApi.notifyExit { urlBuilder ->
                    buildUrl(urlBuilder, host, syncRuntimeInfo.port)
                }
            }
        }
        job.cancel()
    }

    override suspend fun markExit() {
        logger.info { "markExit ${syncRuntimeInfo.appInstanceId}" }
        update {
            this.connectState = SyncState.DISCONNECTED
            this.modifyTime = RealmInstant.now()
        }
    }

    private fun isExistSession(): Boolean {
        return signalProtocolStore.loadSession(signalProtocolAddress) != null
    }

    private fun createSessionBuilder(): SessionBuilder {
        return SessionBuilder(signalProtocolStore, signalProtocolAddress)
    }

    override suspend fun clearContext() {
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
