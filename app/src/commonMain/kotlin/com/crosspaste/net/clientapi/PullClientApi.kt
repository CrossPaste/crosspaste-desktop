package com.crosspaste.net.clientapi

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.pull.PullFileRequest
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.PasteClient
import com.crosspaste.paste.PasteData
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

class PullClientApi(
    private val pasteClient: PasteClient,
    private val configManager: CommonConfigManager,
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
                timeout = 50000L,
                headersBuilder = {
                    append("targetAppInstanceId", targetAppInstanceId)
                    if (configManager.getCurrentConfig().enableEncryptSync) {
                        append("secure", "1")
                    }
                },
                // pull file timeout is 50s
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

    suspend fun pullLatestPaste(
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        val response =
            pasteClient.get(
                headersBuilder = {
                    append("targetAppInstanceId", targetAppInstanceId)
                    if (configManager.getCurrentConfig().enableEncryptSync) {
                        append("secure", "1")
                    }
                },
                urlBuilder = {
                    toUrl()
                    buildUrl("pull", "paste")
                },
            )

        return if (response.status.value == 200) {
            logger.debug { "Success to pull latest paste" }
            SuccessResult(response.body<PasteData>())
        } else {
            runCatching {
                val failResponse = response.body<FailResponse>()
                createFailureResult(
                    StandardErrorCode.SYNC_PASTE_ERROR,
                    "Fail to pull latest paste from $targetAppInstanceId: ${response.status.value} $failResponse",
                )
            }.getOrElse {
                createFailureResult(
                    StandardErrorCode.SYNC_PASTE_ERROR,
                    "Fail to pull latest paste from $targetAppInstanceId: ${response.status.value}",
                )
            }
        }
    }

    private suspend fun result(
        response: HttpResponse,
        api: String,
        key: Any,
    ): ClientApiResult =
        if (response.status.value == 200) {
            logger.debug { "Success to pull $api" }
            SuccessResult(response.bodyAsChannel())
        } else {
            val errorCode =
                if (api == "file") {
                    StandardErrorCode.PULL_FILE_CHUNK_TASK_FAIL
                } else {
                    StandardErrorCode.PULL_ICON_TASK_FAIL
                }
            runCatching {
                val failResponse = response.body<FailResponse>()
                createFailureResult(
                    errorCode,
                    "Fail to pull $api $key: ${response.status.value} $failResponse",
                )
            }.getOrElse {
                createFailureResult(
                    errorCode,
                    "Fail to pull $api $key: ${response.status.value}",
                )
            }
        }
}
