package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.model.sync.SyncInfo

interface SyncDao {

    val database: Database

    fun saveSyncInfo(syncInfo: SyncInfo)

    fun getAllSyncInfos(): List<SyncInfo>
}
