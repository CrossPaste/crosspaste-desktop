package com.clipevery.net.clientapi

import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipData
import com.clipevery.net.ClipClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.reflect.*

class DesktopSendClipClientApi(private val clipClient: ClipClient,
                               private val configManager: ConfigManager): SendClipClientApi {

    private val logger = KotlinLogging.logger {}

    override suspend fun sendClip(
        clipData: ClipData,
        targetAppInstanceId: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit
    ): ClientApiResult {
        val response = clipClient.post(message = clipData,
            messageType = typeInfo<ClipData>(),
            targetAppInstanceId = targetAppInstanceId,
            encrypt = configManager.config.isEncryptSync,
            urlBuilder = toUrl)

        // 422 is the status code for user not allow to receive clip
        return if (response.status.value == 422) {
            logger.debug { "$targetAppInstanceId is not allow to receive clip" }
            SuccessResult()
        } else if (response.status.value != 200) {
            logger.error { "sync clip to $targetAppInstanceId fail" }
            FailureResult(message = response.call.body())
        } else {
            SuccessResult()
        }
    }
}