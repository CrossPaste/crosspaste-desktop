package com.clipevery.dao.sync

import com.clipevery.dto.sync.SyncInfo
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmInstant

class SyncRuntimeInfoRealm(private val realm: Realm): SyncRuntimeInfoDao {

    override fun getAllSyncRuntimeInfos(): RealmResults<SyncRuntimeInfo> {
        return realm.query(SyncRuntimeInfo::class).sort("createTime", Sort.DESCENDING).find()
    }

    override fun getSyncRuntimeInfo(appInstanceId: String): SyncRuntimeInfo? {
        return realm.query(SyncRuntimeInfo::class, "appInstanceId == $0", appInstanceId).first().find()
    }

    override suspend fun getSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo): SyncRuntimeInfo? {
        return realm.write {
            findLatest(syncRuntimeInfo)
        }
    }

    override suspend fun updateConnectState(syncRuntimeInfo: SyncRuntimeInfo, connectState: Int) {
        realm.write {
            findLatest(syncRuntimeInfo)?.apply {
                this.connectState = connectState
            }
        }
    }

    override suspend fun updateConnectInfo(syncRuntimeInfo: SyncRuntimeInfo, connectState: Int, connectHostAddress: String) {
        realm.write {
            findLatest(syncRuntimeInfo)?.apply {
                this.connectState = connectState
                this.connectHostAddress = connectHostAddress
            }
        }
    }

    override fun updateAllowSend(syncRuntimeInfo: SyncRuntimeInfo, allowSend: Boolean): SyncRuntimeInfo? {
        return realm.writeBlocking {
            findLatest(syncRuntimeInfo)?.apply {
                this.allowSend = allowSend
            }
        }
    }

    override fun updateAllowReceive(syncRuntimeInfo: SyncRuntimeInfo, allowReceive: Boolean): SyncRuntimeInfo? {
        return realm.writeBlocking {
            findLatest(syncRuntimeInfo)?.apply {
                this.allowReceive = allowReceive
            }
        }
    }

    private fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo, syncInfo: SyncInfo) {
        var hasModify = false
        if (syncRuntimeInfo.appVersion != syncInfo.appInfo.appVersion) {
            syncRuntimeInfo.appVersion = syncInfo.appInfo.appVersion
            hasModify = true
        }

        if (syncRuntimeInfo.userName != syncInfo.appInfo.userName) {
            syncRuntimeInfo.userName = syncInfo.appInfo.userName
            hasModify = true
        }

        if (syncRuntimeInfo.deviceId != syncInfo.endpointInfo.deviceId) {
            syncRuntimeInfo.deviceId = syncInfo.endpointInfo.deviceId
            hasModify = true
        }

        if (syncRuntimeInfo.deviceName != syncInfo.endpointInfo.deviceName) {
            syncRuntimeInfo.deviceName = syncInfo.endpointInfo.deviceName
            hasModify = true
        }

        if (syncRuntimeInfo.platformName != syncInfo.endpointInfo.platform.name) {
            syncRuntimeInfo.platformName = syncInfo.endpointInfo.platform.name
            hasModify = true
        }

        if (syncRuntimeInfo.platformVersion != syncInfo.endpointInfo.platform.version) {
            syncRuntimeInfo.platformVersion = syncInfo.endpointInfo.platform.version
            hasModify = true
        }

        if (syncRuntimeInfo.platformArch != syncInfo.endpointInfo.platform.arch) {
            syncRuntimeInfo.platformArch = syncInfo.endpointInfo.platform.arch
            hasModify = true
        }

        if (syncRuntimeInfo.platformBitMode != syncInfo.endpointInfo.platform.bitMode) {
            syncRuntimeInfo.platformBitMode = syncInfo.endpointInfo.platform.bitMode
            hasModify = true
        }

        if (syncRuntimeInfo.hostInfoList != syncInfo.endpointInfo.hostInfoList) {
            syncRuntimeInfo.hostInfoList = syncInfo.endpointInfo.hostInfoList.toRealmList()
            hasModify = true
        }

        if (syncRuntimeInfo.port != syncInfo.endpointInfo.port) {
            syncRuntimeInfo.port = syncInfo.endpointInfo.port
            hasModify = true
        }

        if (hasModify) {
            syncRuntimeInfo.modifyTime = RealmInstant.now()
        }
    }

    private fun createSyncRuntimeInfo(syncInfo: SyncInfo): SyncRuntimeInfo {
        return SyncRuntimeInfo().apply {
            appInstanceId = syncInfo.appInfo.appInstanceId
            appVersion = syncInfo.appInfo.appVersion
            userName = syncInfo.appInfo.userName
            deviceId = syncInfo.endpointInfo.deviceId
            deviceName = syncInfo.endpointInfo.deviceName
            platformName = syncInfo.endpointInfo.platform.name
            platformArch = syncInfo.endpointInfo.platform.arch
            platformBitMode = syncInfo.endpointInfo.platform.bitMode
            platformVersion = syncInfo.endpointInfo.platform.version
            hostInfoList = syncInfo.endpointInfo.hostInfoList.toRealmList()
            port = syncInfo.endpointInfo.port
            createTime = RealmInstant.now()
        }
    }

    override fun inertOrUpdate(syncInfo: SyncInfo) {
        realm.writeBlocking {
            query(SyncRuntimeInfo::class, "appInstanceId == $0", syncInfo.appInfo.appInstanceId)
                .first()
                .find()?.let {
                    updateSyncRuntimeInfo(it, syncInfo)
                } ?: run {
                    copyToRealm(createSyncRuntimeInfo(syncInfo))
                }
        }
    }

    override fun inertOrUpdate(syncInfos: List<SyncInfo>): List<String> {
        return realm.writeBlocking {
            return@writeBlocking buildList {
                syncInfos.forEach { syncInfo ->
                    query(
                        SyncRuntimeInfo::class,
                        "appInstanceId == $0",
                        syncInfo.appInfo.appInstanceId
                    )
                        .first()
                        .find()?.let {
                            updateSyncRuntimeInfo(it, syncInfo)
                        } ?: run {
                            copyToRealm(createSyncRuntimeInfo(syncInfo))
                            add(syncInfo.appInfo.appInstanceId)
                    }
                }
            }
        }
    }

    override fun deleteSyncRuntimeInfo(appInstanceId: String) {
        realm.writeBlocking {
            query(SyncRuntimeInfo::class, "appInstanceId == $0", appInstanceId)
                .first()
                .find()?.let {
                    delete(it)
                }
        }
    }
}
