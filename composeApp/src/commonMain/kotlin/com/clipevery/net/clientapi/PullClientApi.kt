package com.clipevery.net.clientapi

import com.clipevery.dto.pull.PullFileRequest
import io.ktor.http.*

interface PullClientApi {

    suspend fun pullFile(
        pullFileRequest: PullFileRequest,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult

    suspend fun pullIcon(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult
}
