package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.dto.sync.SyncInfo

interface SyncInfoDao {

    val database: Database

    fun saveSyncInfo(syncInfo: SyncInfo)

    fun getAllSyncInfos(): List<SyncInfo>
}
