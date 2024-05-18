package com.clipevery.sync

import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dao.sync.SyncState
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.net.SyncInfoFactory
import com.clipevery.net.clientapi.SyncClientApi
import com.clipevery.utils.TelnetUtils
import com.clipevery.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SignalProtocolStore
import java.util.concurrent.atomic.AtomicReference

class DesktopSyncHandler(
    override var syncRuntimeInfo: SyncRuntimeInfo,
    private val tokenCache: TokenCache,
    private val telnetUtils: TelnetUtils,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncClientApi: SyncClientApi,
    private val signalProtocolStore: SignalProtocolStore,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
) : SyncHandler {

    private val logger = KotlinLogging.logger {}

    private val signalProtocolAddress = SignalProtocolAddress(syncRuntimeInfo.appInstanceId, 1)

    override val sessionCipher: SessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)

    private val resolveState = AtomicReference<Boolean?>(null)

    override suspend fun getConnectHostAddress(): String? {
        syncRuntimeInfo.connectHostAddress?.let {
            return it
        } ?: run {
            val syncInfo = syncInfoFactory.createSyncInfo()
            resolveSync(syncInfo, false)
            return syncRuntimeInfo.connectHostAddress
        }
    }

    override suspend fun resolveSync(
        currentDeviceSyncInfo: SyncInfo,
        force: Boolean,
    ) {
        var delaySum = 0
        while (true) {
            if (delaySum >= 1000) {
                break
            } else {
                when {
                    force && resolveState.compareAndSet(null, true) ->
                        try {
                            doResolveSync(currentDeviceSyncInfo, true)
                            break
                        } finally {
                            resolveState.set(null)
                        }

                    !force && resolveState.compareAndSet(null, false) ->
                        try {
                            doResolveSync(currentDeviceSyncInfo, false)
                            break
                        } finally {
                            resolveState.set(null)
                        }

                    force -> while (resolveState.get() == false) {
                        delay(100)
                        delaySum += 100
                    }
                }
            }
        }
    }

    private suspend fun doResolveSync(
        currentDeviceSyncInfo: SyncInfo,
        force: Boolean,
    ) {
        if (force && syncRuntimeInfo.connectHostAddress != null) {
            update {
                this.connectState = SyncState.DISCONNECTED
            } ?: run {
                return
            }
        }

        if (syncRuntimeInfo.connectState != SyncState.CONNECTED) {
            do {
                when (syncRuntimeInfo.connectState) {
                    SyncState.DISCONNECTED -> {
                        resolveDisconnected()
                    }
                    SyncState.CONNECTING -> {
                        resolveConnecting(currentDeviceSyncInfo)
                    }
                    SyncState.UNVERIFIED -> {
                        tokenCache.getToken(syncRuntimeInfo.appInstanceId)?.let { token ->
                            trustByToken(token)
                        }
                        resolveConnecting(currentDeviceSyncInfo)
                    }
                    else -> {
                        logger.warn { "current state is ${syncRuntimeInfo.connectState}" }
                    }
                }
            } while (syncRuntimeInfo.connectState == SyncState.CONNECTING)
        }
    }

    override fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo) {
        this.syncRuntimeInfo = syncRuntimeInfo
    }

    private suspend fun update(block: SyncRuntimeInfo.() -> Unit): SyncRuntimeInfo? {
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
            }
        } ?: run {
            logger.info { "${syncRuntimeInfo.platformName} to disconnected" }
            update {
                this.connectState = SyncState.DISCONNECTED
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
            }
        }
    }

    private suspend fun useSession(
        host: String,
        port: Int,
        syncInfo: SyncInfo,
    ): Boolean {
        try {
            return exchangeSyncInfo(host, port, syncInfo)
        } catch (e: Exception) {
            logger.warn(e) { "useSession exchangeSyncInfo fail" }
        }
        return false
    }

    private suspend fun createSession(
        host: String,
        port: Int,
        syncInfo: SyncInfo,
    ) {
        if (syncClientApi.isTrust { urlBuilder ->
                buildUrl(urlBuilder, host, port, "sync", "isTrust")
            }
        ) {
            try {
                syncClientApi.getPreKeyBundle { urlBuilder ->
                    buildUrl(urlBuilder, host, port, "sync", "preKeyBundle")
                }?.let { preKeyBundle ->
                    val sessionBuilder = createSessionBuilder()
                    try {
                        signalProtocolStore.saveIdentity(signalProtocolAddress, preKeyBundle.identityKey)
                        sessionBuilder.process(preKeyBundle)
                        if (exchangeSyncInfo(host, port, syncInfo)) {
                            return
                        }
                    } catch (e: Exception) {
                        logger.warn(e) { "createSession exchangeSyncInfo fail" }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "createSession getPreKeyBundle fail" }
            }
            logger.info { "connect state to unmatched $host $port" }
            update {
                this.connectState = SyncState.UNMATCHED
            }
        } else {
            logger.info { "connect state to unverified $host $port" }
            update {
                this.connectHostAddress = host
                this.port = port
                this.connectState = SyncState.UNVERIFIED
                logger.info { "createSession ${syncRuntimeInfo.platformName} ${SyncState.UNVERIFIED}" }
            }
        }
    }

    private suspend fun exchangeSyncInfo(
        host: String,
        port: Int,
        syncInfo: SyncInfo,
    ): Boolean {
        return if (syncClientApi.exchangeSyncInfo(
                syncInfo,
                sessionCipher,
            ) { urlBuilder ->
                buildUrl(urlBuilder, host, port, "sync", "exchangeSyncInfo")
            }
        ) {
            update {
                this.connectState = SyncState.CONNECTED
            }
            true
        } else {
            false
        }
    }

    override suspend fun trustByToken(token: Int) {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                syncClientApi.trust(token) { urlBuilder ->
                    buildUrl(urlBuilder, host, syncRuntimeInfo.port, "sync", "trust")
                }
            }
        }
    }

    override suspend fun showToken() {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                if (!syncClientApi.showToken { urlBuilder ->
                        buildUrl(urlBuilder, host, syncRuntimeInfo.port, "sync", "showToken")
                    }
                ) {
                    update {
                        this.connectHostAddress = null
                        this.connectState = SyncState.DISCONNECTED
                    }
                }
            }
        }
    }

    private fun isExistSession(): Boolean {
        return signalProtocolStore.loadSession(signalProtocolAddress) != null
    }

    private fun createSessionBuilder(): SessionBuilder {
        return SessionBuilder(signalProtocolStore, signalProtocolAddress)
    }

    override fun clearContext() {
        // todo clear session
    }
}
