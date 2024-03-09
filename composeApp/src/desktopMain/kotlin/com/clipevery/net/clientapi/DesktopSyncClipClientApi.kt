package com.clipevery.net.clientapi

import com.clipevery.dao.clip.ClipData
import com.clipevery.net.ClipClient
import io.ktor.http.*

class DesktopSyncClipClientApi(private val clipClient: ClipClient): SyncClipClientApi {
    override suspend fun syncClip(
        clipData: ClipData,
        clipBytes: ByteArray,
        toUrl: URLBuilder.(URLBuilder) -> Unit
    ): Int {
        TODO("Not yet implemented")
    }
}