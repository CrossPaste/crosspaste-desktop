package com.clipevery.net.clientapi

import com.clipevery.dao.clip.ClipData
import io.ktor.http.*

interface SyncClipClientApi {

    suspend fun syncClip(clipData: ClipData,
                         clipBytes: ByteArray,
                         toUrl: URLBuilder.(URLBuilder) -> Unit): Int

}

object SyncClipResult {
    const val SUCCESS = 0
    const val FAILED = 1
}