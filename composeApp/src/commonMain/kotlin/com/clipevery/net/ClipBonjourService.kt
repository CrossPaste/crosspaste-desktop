package com.clipevery.net

import com.clipevery.dto.sync.SyncInfo

interface ClipBonjourService {

    fun registerService(): ClipBonjourService

    fun unregisterService(): ClipBonjourService

    suspend fun search(timeMillis: Long = 1000): List<SyncInfo>
}
