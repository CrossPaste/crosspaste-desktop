package com.crosspaste.net.clientapi

import com.crosspaste.config.ConfigManager
import com.crosspaste.dto.pull.PullFileRequest
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.PasteClient
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

class PullClientApi(
    private val pasteClient: PasteClient,
    private val configManager: ConfigManager,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun pullFile(
        pullFileRequest: PullFileRequest,
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        val response =
            pasteClient.post(
                message = pullFileRequest,
                messageType = typeInfo<PullFileRequest>(),
                targetAppInstanceId = targetAppInstanceId,
                encrypt = configManager.getCurrentConfig().enableEncryptSync,
                // pull file timeout is 50s
                timeout = 50000L,
                urlBuilder = {
                    toUrl()
                    buildUrl("pull", "file")
                },
            )

        return result(response, "file", pullFileRequest)
    }

    suspend fun pullIcon(
        source: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        val response =
            pasteClient.get(
                timeout = 50000L,
                urlBuilder = {
                    toUrl()
                    buildUrl("pull", "icon", source)
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
            SuccessResult(response.rawContent)
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
