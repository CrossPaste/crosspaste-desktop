package com.clipevery.net.clientapi

import com.clipevery.config.ConfigManager
import com.clipevery.dto.pull.PullFileRequest
import com.clipevery.net.ClipClient
import com.clipevery.utils.FailResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.reflect.*

class DesktopPullFileClientApi(
    private val clipClient: ClipClient,
    private val configManager: ConfigManager,
) : PullFileClientApi {

    private val logger = KotlinLogging.logger {}

    @OptIn(InternalAPI::class)
    override suspend fun pullFile(
        pullFileRequest: PullFileRequest,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        val response =
            clipClient.post(
                message = pullFileRequest,
                messageType = typeInfo<PullFileRequest>(),
                targetAppInstanceId = pullFileRequest.appInstanceId,
                encrypt = configManager.config.isEncryptSync,
                timeout = 5000L, // pull file timeout is 5s
                urlBuilder = toUrl,
            )

        return if (response.status.value == 200) {
            logger.debug { "Success to pull file $pullFileRequest" }
            SuccessResult(response.content)
        } else {
            val failResponse = response.body<FailResponse>()
            logger.error { "Fail to pull file $pullFileRequest: ${response.status.value} $failResponse" }
            FailureResult("Fail to pull file $pullFileRequest: ${response.status.value} $failResponse")
        }
    }
}
