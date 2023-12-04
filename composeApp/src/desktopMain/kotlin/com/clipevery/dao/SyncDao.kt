package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.model.AppInfo
import com.clipevery.model.RequestEndpointInfo
import com.clipevery.model.SyncState
import org.signal.libsignal.protocol.state.PreKeyBundle

class SyncDao(private val database: Database) {

    fun saveSyncEndpoint(appInfo: AppInfo,
                         requestEndpointInfo: RequestEndpointInfo,
                         preKeyBundle: PreKeyBundle) {
        val appInstanceId = appInfo.appInstanceId
        val appVersion = appInfo.appVersion
        val userName = appInfo.userName
        val deviceId = requestEndpointInfo.deviceInfo.deviceId
        val deviceName = requestEndpointInfo.deviceInfo.deviceName
        val syncState = SyncState.ONLINE
        val hostAddress =


        database.syncQueries.insert(sync.id, sync.lastSyncTime)
    }
}