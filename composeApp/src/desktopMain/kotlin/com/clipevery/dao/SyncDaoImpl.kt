package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.app.AppInfo
import com.clipevery.endpoint.ExplicitEndpointInfo
import com.clipevery.dto.model.SyncInfo
import com.clipevery.dto.model.SyncState
import com.clipevery.net.HostInfo
import com.clipevery.platform.Platform

class SyncDaoImpl(override val database: Database): SyncDao {

    override fun saveSyncInfo(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        val appVersion = syncInfo.appInfo.appVersion
        val userName = syncInfo.appInfo.userName
        val deviceId = syncInfo.endpointInfo.deviceId
        val deviceName = syncInfo.endpointInfo.deviceName
        val syncState = syncInfo.state
        val hostName = syncInfo.endpointInfo.hostInfo.hostName
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
            hostName,
            hostAddress,
            port.toLong(),
            platformName,
            platformArch,
            platformBitMode.toLong(),
            platformVersion
        )
    }

    override fun getAllSyncInfos(): List<SyncInfo> {
        return database.syncQueries.selectAll().executeAsList().map {
            SyncInfo(
                appInfo = AppInfo(
                    appInstanceId = it.app_instance_id,
                    appVersion = it.app_version,
                    userName = it.app_user_name
                ),
                endpointInfo = ExplicitEndpointInfo(
                    deviceId = it.device_id,
                    deviceName = it.device_name,
                    platform = Platform(
                        name = it.platform_name,
                        arch = it.platform_arch,
                        bitMode = it.platform_bit_mode.toInt(),
                        version = it.platform_version
                    ),
                    hostInfo = HostInfo(
                        hostName = it.host_name,
                        hostAddress = it.host_address
                    ),
                    port = it.port.toInt()
                ),
                state = SyncState.valueOf(it.sync_state)
            )
        }
    }
}