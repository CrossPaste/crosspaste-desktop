package com.clipevery.dao

import com.clipevery.dto.sync.SyncInfo
import io.realm.kotlin.Realm

interface SyncInfoDao {

    val realm: Realm

    fun saveSyncInfo(syncInfo: SyncInfo)

    fun getAllSyncInfos(): List<SyncInfo>
}
