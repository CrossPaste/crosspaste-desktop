package com.clipevery.sync

import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dao.sync.SyncState
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.net.SyncInfoFactory
import com.clipevery.net.clientapi.FailureResult
import com.clipevery.net.clientapi.SuccessResult
import com.clipevery.net.clientapi.SyncClientApi
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

            val currentDeviceSyncInfo = getCurrentSyncInfo()

            resolveConnecting(currentDeviceSyncInfo)

            if (syncRuntimeInfo.connectState != SyncState.CONNECTED) {
                failTime++
                return@withLock
            }
        }
        waitNext()
    }

    private fun getCurrentSyncInfo(): SyncInfo {
        return syncInfoFactory.createSyncInfo()
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
            forceResolve()
            return syncRuntimeInfo.connectHostAddress
        }
    }

    override suspend fun forceResolve() {
        mutex.withLock {
            if (syncRuntimeInfo.connectState == SyncState.CONNECTED) {
                val currentDeviceSyncInfo = getCurrentSyncInfo()
                resolveConnecting(currentDeviceSyncInfo)
            }

            if (syncRuntimeInfo.connectState == SyncState.CONNECTED) {
                failTime = 0
                recommendedRefreshTime = computeRefreshTime()
                return@withLock
            }

            resolveDisconnected()
            if (syncRuntimeInfo.connectState != SyncState.CONNECTING) {
                failTime++
                recommendedRefreshTime = computeRefreshTime()
                return@withLock
            }

            val currentDeviceSyncInfo = getCurrentSyncInfo()

            resolveConnecting(currentDeviceSyncInfo)

            if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
                tokenCache.getToken(syncRuntimeInfo.appInstanceId)?.let { token ->
                    trustByToken(token)
                }
                resolveConnecting(currentDeviceSyncInfo)
            }

            if (syncRuntimeInfo.connectState != SyncState.CONNECTED) {
                failTime++
                recommendedRefreshTime = computeRefreshTime()
            } else {
                failTime = 0
                recommendedRefreshTime = computeRefreshTime()
            }
        }
    }

    override fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo) {
        this.syncRuntimeInfo = syncRuntimeInfo
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

    private suspend fun resolveDisconnected() {
        telnetUtils.switchHost(syncRuntimeInfo.hostInfoList, syncRuntimeInfo.port)?.let { hostInfo ->
            logger.info { "${hostInfo.hostAddress} to connecting" }
            update {
                this.connectHostAddress = hostInfo.hostAddress
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

    private suspend fun resolveConnecting(currentDeviceSyncInfo: SyncInfo) {
        syncRuntimeInfo.connectHostAddress?.let { host ->
            if (isExistSession()) {
                if (useSession(host, syncRuntimeInfo.port, currentDeviceSyncInfo)) {
                    return@resolveConnecting
                }
            }
            createSession(host, syncRuntimeInfo.port, currentDeviceSyncInfo)
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
        syncInfo: SyncInfo,
    ): Boolean {
        return exchangeSyncInfo(host, port, syncInfo)
    }

    private suspend fun createSession(
        host: String,
        port: Int,
        syncInfo: SyncInfo,
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
                        exchangeSyncInfo(host, port, syncInfo)
                    }
                }
            }
            is FailureResult -> {
                logger.info { "connect state to unverified $host $port" }
                update {
                    this.connectHostAddress = host
                    this.port = port
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
        syncInfo: SyncInfo,
    ): Boolean {
        val result =
            syncClientApi.exchangeSyncInfo(
                syncInfo,
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

    override fun clearContext() {
        job.cancel()
    }
}
