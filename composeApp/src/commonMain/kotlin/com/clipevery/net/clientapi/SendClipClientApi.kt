package com.clipevery.net.clientapi

import com.clipevery.dao.clip.ClipData
import io.ktor.http.*

interface SendClipClientApi {

    suspend fun sendClip(
        clipData: ClipData,
        targetAppInstanceId: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult
}
