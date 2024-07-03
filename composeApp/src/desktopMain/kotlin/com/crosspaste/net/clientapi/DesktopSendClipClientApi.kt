package com.crosspaste.net.clientapi

import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.ClipClient
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.util.reflect.*

class DesktopSendClipClientApi(
    private val clipClient: ClipClient,
    private val configManager: ConfigManager,
) : SendClipClientApi {

    private val logger = KotlinLogging.logger {}

    override suspend fun sendClip(
        clipData: ClipData,
        targetAppInstanceId: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        val response =
            clipClient.post(
                message = clipData,
                messageType = typeInfo<ClipData>(),
                targetAppInstanceId = targetAppInstanceId,
                encrypt = configManager.config.isEncryptSync,
                urlBuilder = {
                    toUrl(it)
                    buildUrl(it, "sync", "clip")
                },
            )

        // 422 is the status code for user not allow to receive clip
        return if (response.status.value == 422) {
            logger.debug { "$targetAppInstanceId is not allow to receive clip" }
            SuccessResult()
        } else if (response.status.value != 200) {
            createFailureResult(
                StandardErrorCode.SYNC_CLIP_ERROR,
                "sync clip to $targetAppInstanceId fail",
            )
        } else {
            SuccessResult()
        }
    }
}
