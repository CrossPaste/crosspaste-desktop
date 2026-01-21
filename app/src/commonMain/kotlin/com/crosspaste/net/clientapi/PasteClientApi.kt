package com.crosspaste.net.clientapi

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.exception.standardErrorCodeMap
import com.crosspaste.net.PasteClient
import com.crosspaste.paste.PasteData
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.http.*
import io.ktor.util.reflect.*

class PasteClientApi(
    private val pasteClient: PasteClient,
    private val configManager: CommonConfigManager,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun sendPaste(
        pasteData: PasteData,
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        val response =
            pasteClient.post(
                message = pasteData,
                messageType = typeInfo<PasteData>(),
                headersBuilder = {
                    append("targetAppInstanceId", targetAppInstanceId)
                    if (configManager.getCurrentConfig().enableEncryptSync) {
                        append("secure", "1")
                    }
                },
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "paste")
                },
            )

        // 422 is the status code for user not allow to receive paste
        return if (response.status.value == 422) {
            logger.debug { "$targetAppInstanceId is not allow to receive paste" }
            SuccessResult()
        } else if (response.status.value != 200) {
            runCatching {
                val failResponse = response.body<FailResponse>()
                standardErrorCodeMap[failResponse.errorCode]?.let {
                    createFailureResult(it, failResponse.message)
                } ?: run {
                    createFailureResult(
                        StandardErrorCode.SYNC_PASTE_ERROR,
                        "sync paste to $targetAppInstanceId fail: ${failResponse.message}",
                    )
                }
            }.getOrElse {
                createFailureResult(
                    StandardErrorCode.SYNC_PASTE_ERROR,
                    "sync paste to $targetAppInstanceId fail",
                )
            }
        } else {
            SuccessResult()
        }
    }
}
