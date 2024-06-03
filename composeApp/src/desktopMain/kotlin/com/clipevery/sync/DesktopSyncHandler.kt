package com.clipevery.sync

import com.clipevery.dao.signal.SignalDao
import com.clipevery.dao.sync.HostInfo
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dao.sync.SyncState
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.net.SyncInfoFactory
import com.clipevery.net.clientapi.FailureResult
import com.clipevery.net.clientapi.SuccessResult
import com.clipevery.net.clientapi.SyncClientApi
import com.clipevery.utils.DesktopNetUtils.hostPreFixMatch
import com.clipevery.utils.TelnetUtils
import com.clipevery.utils.buildUrl
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
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.SignalProtocolStore
import kotlin.math.min

class DesktopSyncHandler(
    override var syncRuntimeInfo: SyncRuntimeInfo,
    private val tokenCache: TokenCache,
    private val telnetUtils: TelnetUtils,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncClientApi: SyncClientApi,
    private val signalProtocolStore: SignalProtocolStore,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val signalDao: SignalDao,
    scope: CoroutineScope,
) : SyncHandler {

    private val logger = KotlinLogging.logger {}

    private val signalProtocolAddress = SignalProtocolAddress(syncRuntimeInfo.appInstanceId, 1)

    override val sessionCipher: SessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)

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
                if (useSession(host, syncRuntimeInfo.port)) {
                    return@resolveConnecting
                }
            }
            createSession(host, syncRuntimeInfo.port)
        } ?: run {
            logger.info { "${syncRuntimeInfo.platformName} to disconnected" }
            update {
                this.connectState = SyncState.DISCONNECTED
                this.modifyTime = RealmInstant.now()
            }
        }
    }

    private suspend fun useSession(
        host: String,
        port: Int,
    ): Boolean {
        return exchangeSyncInfo(host, port)
    }

    private suspend fun createSession(
        host: String,
        port: Int,
    ) {
        val result =
            syncClientApi.isTrust { urlBuilder ->
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
                        exchangeSyncInfo(host, port)
                    }
                }
            }
            is FailureResult -> {
                logger.info { "connect state to unverified $host $port" }
                update {
                    this.connectState = SyncState.UNVERIFIED
                    this.modifyTime = RealmInstant.now()
                    logger.info { "createSession ${syncRuntimeInfo.platformName} UNVERIFIED" }
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

    private suspend fun exchangeSyncInfo(
        host: String,
        port: Int,
    ): Boolean {
        val result =
            syncClientApi.exchangeSyncInfo(
                getCurrentSyncInfo(),
                sessionCipher,
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
                logger.info { "exchangeSyncInfo return fail state to unmatched $host $port" }
                update {
                    this.connectState = SyncState.UNMATCHED
                    this.modifyTime = RealmInstant.now()
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
    }

    override suspend fun markExit() {
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
