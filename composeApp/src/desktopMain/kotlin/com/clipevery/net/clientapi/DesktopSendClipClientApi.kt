package com.clipevery.net.clientapi

import com.clipevery.dao.clip.ClipData
import com.clipevery.net.ClipClient
import io.ktor.http.*
import io.ktor.util.reflect.*

class DesktopSendClipClientApi(private val clipClient: ClipClient): SendClipClientApi {
    override suspend fun sendClip(
        clipData: ClipData,
        toUrl: URLBuilder.(URLBuilder) -> Unit
    ): Int {
        val response = clipClient.post(message = clipData,
            messageType = typeInfo<ClipData>(),
            urlBuilder = toUrl)

        // 422 is the status code for user not allow to receive clip
        return if (response.status.value == 422) {
            SyncClipResult.SUCCESS
        } else if (response.status.value != 200) {
            SyncClipResult.FAILED
        } else {
            SyncClipResult.SUCCESS
        }
    }
}