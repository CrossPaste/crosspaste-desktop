package com.clipevery.dao.sync

import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmInstant

class SyncRuntimeInfoRealm(private val realm: Realm) : SyncRuntimeInfoDao {

    override fun getAllSyncRuntimeInfos(): RealmResults<SyncRuntimeInfo> {
        return realm.query(SyncRuntimeInfo::class).sort("createTime", Sort.DESCENDING).find()
    }

    override fun getSyncRuntimeInfo(appInstanceId: String): SyncRuntimeInfo? {
        return realm.query(SyncRuntimeInfo::class, "appInstanceId == $0", appInstanceId).first().find()
    }

    override fun update(
        syncRuntimeInfo: SyncRuntimeInfo,
        block: SyncRuntimeInfo.() -> Unit,
    ): SyncRuntimeInfo? {
        return realm.writeBlocking {
            findLatest(syncRuntimeInfo)?.let {
                return@writeBlocking it.apply(block)
            } ?: run {
                query(SyncRuntimeInfo::class, "appInstanceId == $0", syncRuntimeInfo.appInstanceId).first().find()?.let {
                    return@writeBlocking it.apply(block)
                }
            }
        }
    }

    override suspend fun suspendUpdate(
        syncRuntimeInfo: SyncRuntimeInfo,
        block: SyncRuntimeInfo.() -> Unit,
    ): SyncRuntimeInfo? {
        return realm.write {
            findLatest(syncRuntimeInfo)?.let {
                return@write it.apply(block)
            } ?: run {
                query(SyncRuntimeInfo::class, "appInstanceId == $0", syncRuntimeInfo.appInstanceId).first().find()?.let {
                    return@write it.apply(block)
                }
            }
        }
    }

    private fun updateSyncRuntimeInfo(
        syncRuntimeInfo: SyncRuntimeInfo,
        newSyncRuntimeInfo: SyncRuntimeInfo,
    ): Boolean {
        var hasModify = false
        if (syncRuntimeInfo.appVersion != newSyncRuntimeInfo.appVersion) {
            syncRuntimeInfo.appVersion = newSyncRuntimeInfo.appVersion
            hasModify = true
        }

        if (syncRuntimeInfo.userName != newSyncRuntimeInfo.userName) {
            syncRuntimeInfo.userName = newSyncRuntimeInfo.userName
            hasModify = true
        }

        if (syncRuntimeInfo.deviceId != newSyncRuntimeInfo.deviceId) {
            syncRuntimeInfo.deviceId = newSyncRuntimeInfo.deviceId
            hasModify = true
        }

        if (syncRuntimeInfo.deviceName != newSyncRuntimeInfo.deviceName) {
            syncRuntimeInfo.deviceName = newSyncRuntimeInfo.deviceName
            hasModify = true
        }

        if (syncRuntimeInfo.platformName != newSyncRuntimeInfo.platformName) {
            syncRuntimeInfo.platformName = newSyncRuntimeInfo.platformName
            hasModify = true
        }

        if (syncRuntimeInfo.platformVersion != newSyncRuntimeInfo.platformVersion) {
            syncRuntimeInfo.platformVersion = newSyncRuntimeInfo.platformVersion
            hasModify = true
        }

        if (syncRuntimeInfo.platformArch != newSyncRuntimeInfo.platformArch) {
            syncRuntimeInfo.platformArch = newSyncRuntimeInfo.platformArch
            hasModify = true
        }

        if (syncRuntimeInfo.platformBitMode != newSyncRuntimeInfo.platformBitMode) {
            syncRuntimeInfo.platformBitMode = newSyncRuntimeInfo.platformBitMode
            hasModify = true
        }

        if (!hostInfoListEqual(syncRuntimeInfo.hostInfoList, newSyncRuntimeInfo.hostInfoList)) {
            syncRuntimeInfo.hostInfoList = newSyncRuntimeInfo.hostInfoList
            hasModify = true
        }

        if (syncRuntimeInfo.port != newSyncRuntimeInfo.port) {
            syncRuntimeInfo.port = newSyncRuntimeInfo.port
            hasModify = true
        }

        // When the state is not connected,
        // we will update the modifyTime at least to drive the refresh
        if (hasModify || syncRuntimeInfo.connectState != SyncState.CONNECTED) {
            syncRuntimeInfo.modifyTime = RealmInstant.now()
        }
        return hasModify
    }

    override fun insertOrUpdate(syncRuntimeInfo: SyncRuntimeInfo): Boolean {
        try {
            return realm.writeBlocking {
                query(SyncRuntimeInfo::class, "appInstanceId == $0", syncRuntimeInfo.appInstanceId)
                    .first()
                    .find()?.let {
                        return@let updateSyncRuntimeInfo(it, syncRuntimeInfo)
                    } ?: run {
                    copyToRealm(syncRuntimeInfo)
                    return@run true
                }
            }
        } catch (e: Exception) {
            return false
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
