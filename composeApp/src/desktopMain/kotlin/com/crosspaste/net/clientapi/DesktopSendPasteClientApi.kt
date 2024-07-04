package com.crosspaste.net.clientapi

import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.PasteClient
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.util.reflect.*

class DesktopSendPasteClientApi(
    private val pasteClient: PasteClient,
    private val configManager: ConfigManager,
) : SendPasteClientApi {

    private val logger = KotlinLogging.logger {}

    override suspend fun sendPaste(
        pasteData: PasteData,
        targetAppInstanceId: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        val response =
            pasteClient.post(
                message = pasteData,
                messageType = typeInfo<PasteData>(),
                targetAppInstanceId = targetAppInstanceId,
                encrypt = configManager.config.isEncryptSync,
                urlBuilder = {
                    toUrl(it)
                    buildUrl(it, "sync", "paste")
                },
            )

        // 422 is the status code for user not allow to receive paste
        return if (response.status.value == 422) {
            logger.debug { "$targetAppInstanceId is not allow to receive paste" }
            SuccessResult()
        } else if (response.status.value != 200) {
            createFailureResult(
                StandardErrorCode.SYNC_PASTE_ERROR,
                "sync paste to $targetAppInstanceId fail",
            )
        } else {
            SuccessResult()
        }
    }
}
