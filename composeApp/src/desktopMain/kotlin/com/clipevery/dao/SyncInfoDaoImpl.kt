package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.app.AppInfo
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.endpoint.EndpointInfo
import com.clipevery.platform.Platform
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SyncInfoDaoImpl(override val database: Database): SyncInfoDao {

    override fun saveSyncInfo(syncInfo: SyncInfo) {
        val appInstanceId = syncInfo.appInfo.appInstanceId
        val appVersion = syncInfo.appInfo.appVersion
        val userName = syncInfo.appInfo.userName
        val deviceId = syncInfo.endpointInfo.deviceId
        val deviceName = syncInfo.endpointInfo.deviceName
        val hostDetails = Json.encodeToString(syncInfo.endpointInfo.hostInfoList)
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
            hostDetails,
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
                endpointInfo = EndpointInfo(
                    deviceId = it.device_id,
                    deviceName = it.device_name,
                    platform = Platform(
                        name = it.platform_name,
                        arch = it.platform_arch,
                        bitMode = it.platform_bit_mode.toInt(),
                        version = it.platform_version
                    ),
                    hostInfoList = Json.decodeFromString(it.host_details),
                    port = it.port.toInt()
                )
            )
        }
    }
}