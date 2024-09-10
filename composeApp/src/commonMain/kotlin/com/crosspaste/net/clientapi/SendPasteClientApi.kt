package com.crosspaste.net.clientapi

import com.crosspaste.realm.paste.PasteData
import io.ktor.http.*

interface SendPasteClientApi {

    suspend fun sendPaste(
        pasteData: PasteData,
        targetAppInstanceId: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult
}
