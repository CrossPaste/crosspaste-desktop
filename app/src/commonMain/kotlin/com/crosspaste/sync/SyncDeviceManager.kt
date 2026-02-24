package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging

class SyncDeviceManager(
    private val secureStore: SecureStore,
    private val syncClientApi: SyncClientApi,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun updateAllowSend(
        syncRuntimeInfo: SyncRuntimeInfo,
        allowSend: Boolean,
    ) {
        syncRuntimeInfoDao.updateAllowSend(syncRuntimeInfo.copy(allowSend = allowSend))
    }

    suspend fun updateAllowReceive(
        syncRuntimeInfo: SyncRuntimeInfo,
        allowReceive: Boolean,
    ) {
        syncRuntimeInfoDao.updateAllowReceive(syncRuntimeInfo.copy(allowReceive = allowReceive))
    }

    suspend fun updateNoteName(
        syncRuntimeInfo: SyncRuntimeInfo,
        noteName: String,
    ) {
        syncRuntimeInfoDao.updateNoteName(syncRuntimeInfo.copy(noteName = noteName))
    }

    suspend fun showToken(syncRuntimeInfo: SyncRuntimeInfo) {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
                val result =
                    syncClientApi.showToken {
                        buildUrl(hostAndPort)
                    }
                if (result is SuccessResult) {
                    logger.info { "showToken success $host ${syncRuntimeInfo.port}" }
                } else {
                    syncRuntimeInfoDao.updateConnectInfo(
                        syncRuntimeInfo.copy(
                            connectState = SyncState.DISCONNECTED,
                            modifyTime = nowEpochMilliseconds(),
                        ),
                    )
                }
            }
        }
    }

    suspend fun notifyExit(syncRuntimeInfo: SyncRuntimeInfo) {
        if (syncRuntimeInfo.connectState == SyncState.CONNECTED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
                syncClientApi.notifyExit {
                    buildUrl(hostAndPort)
                }
            }
        }
    }

    suspend fun markExit(syncRuntimeInfo: SyncRuntimeInfo) {
        logger.info { "markExit ${syncRuntimeInfo.appInstanceId}" }
        syncRuntimeInfoDao.updateConnectInfo(
            syncRuntimeInfo.copy(
                connectState = SyncState.DISCONNECTED,
                modifyTime = nowEpochMilliseconds(),
            ),
        )
    }

    suspend fun removeDevice(syncRuntimeInfo: SyncRuntimeInfo) {
        secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId)
        syncRuntimeInfoDao.deleteSyncRuntimeInfo(syncRuntimeInfo.appInstanceId)
        syncRuntimeInfo.connectHostAddress?.let { host ->
            val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
            syncClientApi.notifyRemove {
                buildUrl(hostAndPort)
            }
        }
    }
}
