package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.dto.model.SyncInfo

interface SyncDao {

    val database: Database

    fun saveSyncInfo(syncInfo: SyncInfo)

    fun getAllSyncInfos(): List<SyncInfo>
}
