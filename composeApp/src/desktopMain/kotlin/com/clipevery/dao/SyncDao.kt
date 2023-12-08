package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.model.SyncInfo

class SyncDao(private val database: Database) {

    fun saveSyncEndpoint(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        val appVersion = syncInfo.appInfo.appVersion
        val userName = syncInfo.appInfo.userName
        val deviceId = syncInfo.endpointInfo.deviceId
        val deviceName = syncInfo.endpointInfo.deviceName
        val syncState = syncInfo.state
        val hostAddress = syncInfo.endpointInfo.hostInfo.hostAddress
        val port = syncInfo.endpointInfo.port
        val platformName = syncInfo.endpointInfo.platform.name
        val platformArch = syncInfo.endpointInfo.platform.arch
        val platformBitMode = syncInfo.endpointInfo.platform.bitMode
        val platformVersion = syncInfo.endpointInfo.platform.version

        database.syncQueries.insert(
            appInstanceId,
            appVersion,
            userName,
            deviceId,
            deviceName,
            syncState.name,
            hostAddress,
            port.toLong(),
            platformName,
            platformArch,
            platformBitMode.toLong(),
            platformVersion
        )
    }
}