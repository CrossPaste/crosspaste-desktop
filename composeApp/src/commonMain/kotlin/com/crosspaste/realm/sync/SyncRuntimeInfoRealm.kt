package com.crosspaste.realm.sync

import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmInstant
import kotlin.reflect.KMutableProperty1

class SyncRuntimeInfoRealm(private val realm: Realm) {

    fun getAllSyncRuntimeInfos(): RealmResults<SyncRuntimeInfo> {
        return realm.query(SyncRuntimeInfo::class).sort("createTime", Sort.DESCENDING).find()
    }

    fun getSyncRuntimeInfo(appInstanceId: String): SyncRuntimeInfo? {
        return realm.query(SyncRuntimeInfo::class, "appInstanceId == $0", appInstanceId).first().find()
    }

    fun update(
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

    suspend fun suspendUpdate(
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

        fun <T> updateField(
            field: KMutableProperty1<SyncRuntimeInfo, T>,
            isNetField: Boolean = false,
            customEquals: ((T, T) -> Boolean)? = null,
        ): Boolean {
            val oldValue = field.get(syncRuntimeInfo)
            val newValue = field.get(newSyncRuntimeInfo)
            val areEqual = customEquals?.invoke(oldValue, newValue) ?: (oldValue == newValue)
            return if (!areEqual) {
                field.set(syncRuntimeInfo, newValue)
                if (isNetField) {
                    netChange = true
                } else {
                    infoChange = true
                }
                true
            } else {
                false
            }
        }

        // Update network-related fields
        updateField(SyncRuntimeInfo::hostInfoList, true, ::hostInfoListEqual)
        updateField(SyncRuntimeInfo::port, true)

        // Update info-related fields
        updateField(SyncRuntimeInfo::appVersion)
        updateField(SyncRuntimeInfo::userName)
        updateField(SyncRuntimeInfo::deviceId)
        updateField(SyncRuntimeInfo::deviceName)
        updateField(SyncRuntimeInfo::platformName)
        updateField(SyncRuntimeInfo::platformVersion)
        updateField(SyncRuntimeInfo::platformArch)
        updateField(SyncRuntimeInfo::platformBitMode)

        // Update modifyTime if necessary
        if (netChange || infoChange || syncRuntimeInfo.connectState != SyncState.CONNECTED) {
            syncRuntimeInfo.modifyTime = RealmInstant.now()
        }

        return when {
            netChange -> ChangeType.NET_CHANGE
            infoChange -> ChangeType.INFO_CHANGE
            else -> ChangeType.NO_CHANGE
        }
    }

    fun insertOrUpdate(syncRuntimeInfo: SyncRuntimeInfo): ChangeType {
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
        } catch (_: Exception) {
            ChangeType.NO_CHANGE
        }
    }

    fun update(syncRuntimeInfos: List<SyncRuntimeInfo>): List<String> {
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deleteSyncRuntimeInfo(appInstanceId: String) {
        realm.writeBlocking {
            query(SyncRuntimeInfo::class, "appInstanceId == $0", appInstanceId)
                .first()
                .find()?.let {
                    delete(it)
                }
        }
    }
}

enum class ChangeType {
    NEW_INSTANCE,
    NO_CHANGE,
    NET_CHANGE,
    INFO_CHANGE,
}
