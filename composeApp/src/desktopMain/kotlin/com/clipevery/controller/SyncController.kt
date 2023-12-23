package com.clipevery.controller

import com.clipevery.app.AppInfo
import com.clipevery.dao.SyncDao
import com.clipevery.utils.decodePreKeyBundle
import com.clipevery.endpoint.EndpointInfoFactory
import com.clipevery.endpoint.ExplicitEndpointInfo
import com.clipevery.exception.ClipException
import com.clipevery.exception.StandardErrorCode
import com.clipevery.dto.model.SyncInfo
import com.clipevery.dto.model.RequestSyncInfo
import com.clipevery.dto.model.ResponseSyncInfo
import com.clipevery.dto.model.SyncState
import com.clipevery.net.SyncInfoWithPreKeyBundle
import com.clipevery.net.SyncValidator
import com.clipevery.signal.ClipIdentityKeyStore
import com.clipevery.utils.telnet
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import java.io.IOException

class SyncController(private val appInfo: AppInfo,
                     private val syncDao: SyncDao,
                     private val sessionStore: SessionStore,
                     private val preKeyStore: PreKeyStore,
                     private val signedPreKeyStore: SignedPreKeyStore,
                     private val identityKeyStore: ClipIdentityKeyStore,
                     private val endpointInfoFactory: EndpointInfoFactory): SyncValidator {

    fun receiveEndpointSyncInfo(requestSyncInfo: RequestSyncInfo): ResponseSyncInfo {
        val (syncInfo, preKeyBundle) = runBlocking { validate(requestSyncInfo) }
        val signalProtocolAddress = SignalProtocolAddress(syncInfo.appInfo.appInstanceId, 1)
        val sessionBuilder = SessionBuilder(sessionStore, preKeyStore, signedPreKeyStore, identityKeyStore, signalProtocolAddress)
        syncDao.database.transaction {
            syncDao.saveSyncInfo(syncInfo)
            sessionBuilder.process(preKeyBundle)
        }
        return ResponseSyncInfo(appInfo, endpointInfoFactory.createEndpointInfo())
    }

    private var token: Int? = null
    private var generateTime: Long = 0
    private val refreshTime: Long = 10000

    override fun createToken(): Int {
        token = (0..999999).random()
        generateTime = System.currentTimeMillis()
        return token!!
    }

    override fun getCurrentToken(): Int {
        return token ?: createToken()
    }

    override fun getRefreshTime(): Long {
        return refreshTime
    }

    override suspend fun validate(requestSyncInfo: RequestSyncInfo): SyncInfoWithPreKeyBundle {
        val currentTimeMillis = System.currentTimeMillis()
        if (currentTimeMillis - generateTime > refreshTime) {
            throw ClipException(StandardErrorCode.SYNC_TIMEOUT.toErrorCode(), "token expired")
        } else if (requestSyncInfo.token != this.token) {
            throw ClipException(StandardErrorCode.SYNC_INVALID.toErrorCode(), "token invalid")
        }
        val hostInfoList = requestSyncInfo.endpointInfo.hostInfoList
        if (hostInfoList.isEmpty()) {
            throw ClipException(StandardErrorCode.SYNC_INVALID.toErrorCode(), "cant find host info")
        }
        val host: String = telnet(
            requestSyncInfo.endpointInfo.hostInfoList.map { hostInfo -> hostInfo.hostAddress },
            requestSyncInfo.endpointInfo.port, 1000
        ) ?: throw ClipException(StandardErrorCode.SYNC_INVALID.toErrorCode(), "cant find telnet success host")
        val hostInfo = hostInfoList.find { hostInfo -> hostInfo.hostAddress == host }!!


        val preKeyBundleBytes = requestSyncInfo.preKeyBundle
        val preKeyBundle: PreKeyBundle = try {
            decodePreKeyBundle(preKeyBundleBytes)
        } catch (e: IOException) {
            throw ClipException(StandardErrorCode.SYNC_INVALID.toErrorCode(), e)
        } catch (e: InvalidKeyException) {
            throw ClipException(StandardErrorCode.SYNC_INVALID.toErrorCode(), e)
        }

        val appInfo = requestSyncInfo.appInfo

        val endpointInfo = ExplicitEndpointInfo(
            requestSyncInfo.endpointInfo.deviceId,
            requestSyncInfo.endpointInfo.deviceName,
            requestSyncInfo.endpointInfo.platform,
            hostInfo, requestSyncInfo.endpointInfo.port
        )

        return SyncInfoWithPreKeyBundle(SyncInfo(appInfo, endpointInfo, SyncState.ONLINE), preKeyBundle)
    }
}

