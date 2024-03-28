package com.clipevery.net.clientapi

import com.clipevery.dto.pull.PullFileRequest
import io.ktor.http.*

interface PullFileClientApi {

    suspend fun pullFile(pullFileRequest: PullFileRequest,
                         toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult

}