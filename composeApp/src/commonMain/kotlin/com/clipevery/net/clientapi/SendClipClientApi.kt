package com.clipevery.net.clientapi

import com.clipevery.dao.clip.ClipData
import io.ktor.http.*

interface SendClipClientApi {

    suspend fun sendClip(clipData: ClipData,
                         clipBytes: ByteArray,
                         toUrl: URLBuilder.(URLBuilder) -> Unit): Int

}

object SyncClipResult {
    const val SUCCESS = 0
    const val FAILED = 1
}