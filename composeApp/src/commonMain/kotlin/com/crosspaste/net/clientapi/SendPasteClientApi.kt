package com.crosspaste.net.clientapi

import com.crosspaste.dao.paste.PasteData
import io.ktor.http.*

interface SendPasteClientApi {

    suspend fun sendPaste(
        pasteData: PasteData,
        targetAppInstanceId: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult
}
