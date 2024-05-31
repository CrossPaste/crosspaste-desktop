package com.clipevery.net.clientapi

import com.clipevery.config.ConfigManager
import com.clipevery.dto.pull.PullFileRequest
import com.clipevery.exception.StandardErrorCode
import com.clipevery.net.ClipClient
import com.clipevery.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.reflect.*

class DesktopPullClientApi(
    private val clipClient: ClipClient,
    private val configManager: ConfigManager,
) : PullClientApi {

    private val logger = KotlinLogging.logger {}

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
                urlBuilder = {
                    toUrl(it)
                    buildUrl(it, "pull", "file")
                },
            )

        return result(response, "file", pullFileRequest)
    }

    override suspend fun pullIcon(
        source: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        val response =
            clipClient.get(
                timeout = 5000L,
                urlBuilder = {
                    toUrl(it)
                    buildUrl(it, "pull", "icon", source)
                },
            )

        return result(response, "icon", response.request.url)
    }

    @OptIn(InternalAPI::class)
    private suspend fun result(
        response: HttpResponse,
        api: String,
        key: Any,
    ): ClientApiResult {
        return if (response.status.value == 200) {
            logger.debug { "Success to pull $api" }
            SuccessResult(response.content)
        } else {
            val failResponse = response.body<FailResponse>()
            createFailureResult(
                if (api == "file") {
                    StandardErrorCode.PULL_FILE_CHUNK_TASK_FAIL
                } else {
                    StandardErrorCode.PULL_ICON_TASK_FAIL
                },
                "Fail to pull $api $key: ${response.status.value} $failResponse",
            )
        }
    }
}
