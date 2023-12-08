package com.clipevery.controller

import com.clipevery.dao.SyncDao
import com.clipevery.encrypt.decodePreKeyBundle
import com.clipevery.exception.ClipException
import com.clipevery.exception.StandardErrorCode
import com.clipevery.model.EndpointInfo
import com.clipevery.model.SyncInfo
import com.clipevery.model.SyncState
import com.clipevery.model.sync.RequestSyncInfo
import com.clipevery.net.SyncInfoWithPreKeyBundle
import com.clipevery.net.SyncValidator
import com.clipevery.utils.telnet
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.state.PreKeyBundle
import java.io.IOException

class SyncController(private val syncDao: SyncDao): SyncValidator {

    fun receiveEndpoint(requestSyncInfo: RequestSyncInfo): RequestSyncInfo {
        val (syncInfo, preKeyBundle) = runBlocking { validate(requestSyncInfo) }
        syncDao.saveSyncEndpoint(syncInfo)
        return requestSyncInfo
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
        val hostInfoList = requestSyncInfo.requestEndpointInfo.deviceInfo.hostInfoList
        if (hostInfoList.isEmpty()) {
            throw ClipException(StandardErrorCode.SYNC_INVALID.toErrorCode(), "cant find host info")
        }
        val host: String = telnet(
            requestSyncInfo.requestEndpointInfo.deviceInfo.hostInfoList.map { hostInfo -> hostInfo.hostAddress },
            requestSyncInfo.requestEndpointInfo.port, 1000
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

        val endpointInfo = EndpointInfo(
            requestSyncInfo.requestEndpointInfo.deviceInfo.deviceId,
            requestSyncInfo.requestEndpointInfo.deviceInfo.deviceName,
            requestSyncInfo.requestEndpointInfo.deviceInfo.platform,
            hostInfo, requestSyncInfo.requestEndpointInfo.port
        )

        return SyncInfoWithPreKeyBundle(SyncInfo(appInfo, endpointInfo, SyncState.ONLINE), preKeyBundle)
    }
}

