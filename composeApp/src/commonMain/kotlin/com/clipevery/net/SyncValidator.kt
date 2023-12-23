package com.clipevery.net

import com.clipevery.exception.ClipException
import com.clipevery.dto.model.SyncInfo
import com.clipevery.dto.model.RequestSyncInfo
import org.signal.libsignal.protocol.state.PreKeyBundle

interface SyncValidator {

    fun createToken(): Int

    fun getCurrentToken(): Int

    fun getRefreshTime(): Long

    @Throws(ClipException::class)
    suspend fun validate(requestSyncInfo: RequestSyncInfo): SyncInfoWithPreKeyBundle
}

data class SyncInfoWithPreKeyBundle(val syncInfo: SyncInfo, val preKeyBundle: PreKeyBundle)
