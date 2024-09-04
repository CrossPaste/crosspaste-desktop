package com.crosspaste.dao.sync

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
    ): ChangeType {
        var netChange = false
        var infoChange = false

        if (!hostInfoListEqual(syncRuntimeInfo.hostInfoList, newSyncRuntimeInfo.hostInfoList)) {
            syncRuntimeInfo.hostInfoList = newSyncRuntimeInfo.hostInfoList
            netChange = true
        }

        if (syncRuntimeInfo.port != newSyncRuntimeInfo.port) {
            syncRuntimeInfo.port = newSyncRuntimeInfo.port
            netChange = true
        }

        if (syncRuntimeInfo.appVersion != newSyncRuntimeInfo.appVersion) {
            syncRuntimeInfo.appVersion = newSyncRuntimeInfo.appVersion
            infoChange = true
        }

        if (syncRuntimeInfo.userName != newSyncRuntimeInfo.userName) {
            syncRuntimeInfo.userName = newSyncRuntimeInfo.userName
            infoChange = true
        }

        if (syncRuntimeInfo.deviceId != newSyncRuntimeInfo.deviceId) {
            syncRuntimeInfo.deviceId = newSyncRuntimeInfo.deviceId
            infoChange = true
        }

        if (syncRuntimeInfo.deviceName != newSyncRuntimeInfo.deviceName) {
            syncRuntimeInfo.deviceName = newSyncRuntimeInfo.deviceName
            infoChange = true
        }

        if (syncRuntimeInfo.platformName != newSyncRuntimeInfo.platformName) {
            syncRuntimeInfo.platformName = newSyncRuntimeInfo.platformName
            infoChange = true
        }

        if (syncRuntimeInfo.platformVersion != newSyncRuntimeInfo.platformVersion) {
            syncRuntimeInfo.platformVersion = newSyncRuntimeInfo.platformVersion
            infoChange = true
        }

        if (syncRuntimeInfo.platformArch != newSyncRuntimeInfo.platformArch) {
            syncRuntimeInfo.platformArch = newSyncRuntimeInfo.platformArch
            infoChange = true
        }

        if (syncRuntimeInfo.platformBitMode != newSyncRuntimeInfo.platformBitMode) {
            syncRuntimeInfo.platformBitMode = newSyncRuntimeInfo.platformBitMode
            infoChange = true
        }

        // When the state is not connected,
        // we will update the modifyTime at least to drive the refresh
        if (netChange || infoChange || syncRuntimeInfo.connectState != SyncState.CONNECTED) {
            syncRuntimeInfo.modifyTime = RealmInstant.now()
        }

        return if (netChange) {
            ChangeType.NET_CHANGE
        } else if (infoChange) {
            ChangeType.INFO_CHANGE
        } else {
            ChangeType.NO_CHANGE
        }
    }

    override fun insertOrUpdate(syncRuntimeInfo: SyncRuntimeInfo): ChangeType {
        return try {
            realm.writeBlocking {
                query(SyncRuntimeInfo::class, "appInstanceId == $0", syncRuntimeInfo.appInstanceId)
                    .first()
                    .find()?.let {
                        return@let updateSyncRuntimeInfo(it, syncRuntimeInfo)
                    } ?: run {
                    copyToRealm(syncRuntimeInfo)
                    return@run ChangeType.NEW_INSTANCE
                }
            }
        } catch (e: Exception) {
            ChangeType.NO_CHANGE
        }
    }

    override fun update(syncRuntimeInfos: List<SyncRuntimeInfo>): List<String> {
        return try {
            realm.writeBlocking {
                val ids = mutableListOf<String>()
                for (syncRuntimeInfo in syncRuntimeInfos) {
                    query(SyncRuntimeInfo::class, "appInstanceId == $0", syncRuntimeInfo.appInstanceId)
                        .first()
                        .find()?.let {
                            val changeType = updateSyncRuntimeInfo(it, syncRuntimeInfo)
                            if (changeType == ChangeType.NET_CHANGE) {
                                ids.add(it.appInstanceId)
                            }
                        }
                }
                ids
            }
        } catch (ignore: Exception) {
            emptyList()
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
