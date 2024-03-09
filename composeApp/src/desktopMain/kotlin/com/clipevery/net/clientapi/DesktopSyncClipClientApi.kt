package com.clipevery.net.clientapi

import com.clipevery.net.ClipClient
import io.ktor.http.*

class DesktopSyncClipClientApi(private val clipClient: ClipClient): SyncClipClientApi {

    override suspend fun syncClip(clipBytes: ByteArray,
                                  toUrl: URLBuilder.(URLBuilder) -> Unit): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun syncClipResources(): Boolean {
        TODO("Not yet implemented")
    }
}