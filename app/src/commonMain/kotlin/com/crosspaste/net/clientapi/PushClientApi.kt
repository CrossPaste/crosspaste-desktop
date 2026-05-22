package com.crosspaste.net.clientapi

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.push.PushCompleteResponse
import com.crosspaste.dto.push.PushHeaders
import com.crosspaste.dto.push.PushPrepareResponse
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.PasteClient
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.paste.PasteData
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.reflect.*

/**
 * Client side of the file push protocol (M4). Mirrors the three desktop server
 * endpoints introduced in M1+M2:
 *
 *  - `POST /sync/paste` with `X-Sync-Mode: push` → returns [PushPrepareResponse]
 *    (token + chunkCount + chunkSize + needIcon).
 *  - `POST /sync/file/push` per-chunk upload, headers carry pasteId / chunkIndex / token.
 *  - `POST /sync/paste/push/complete` finalizes the session, body lists any
 *    chunks the server still considers missing.
 *  - `POST /sync/icon/push/{source}` mirrors `/pull/icon/{source}` for active icon push.
 *
 * Encryption is transparent: when `enableEncryptSync` is on the `secure: 1`
 * header is set and [com.crosspaste.net.plugin.ClientEncryptPlugin] rewrites
 * the ByteArrayContent at HttpSendPipeline.Before. The server route reads
 * bytes from `call.receiveChannel()` and the server decrypt plugin handles
 * the inverse transform — no SecureStore plumbing needed here.
 */
class PushClientApi(
    private val pasteClient: PasteClient,
    private val configManager: CommonConfigManager,
    private val exceptionHandler: ExceptionHandler,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun preparePush(
        pasteData: PasteData,
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        request(logger, exceptionHandler, request = {
            pasteClient.post(
                message = pasteData,
                messageType = typeInfo<PasteData>(),
                headersBuilder = {
                    append("targetAppInstanceId", targetAppInstanceId)
                    append(PushHeaders.SYNC_MODE, PushHeaders.SYNC_MODE_PUSH)
                    if (configManager.getCurrentConfig().enableEncryptSync) {
                        append("secure", "1")
                    }
                },
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "paste")
                },
            )
        }) { response ->
            response.body<PushPrepareResponse>()
        }

    suspend fun pushChunk(
        pasteId: Long,
        chunkIndex: Int,
        sessionToken: String,
        targetAppInstanceId: String,
        chunkBytes: ByteArray,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        safeApiCall(logger, exceptionHandler) {
            val response =
                pasteClient.postBinary(
                    bytes = chunkBytes,
                    timeout = 50_000L,
                    headersBuilder = {
                        append("targetAppInstanceId", targetAppInstanceId)
                        append(PushHeaders.PASTE_ID, pasteId.toString())
                        append(PushHeaders.CHUNK_INDEX, chunkIndex.toString())
                        append(PushHeaders.SESSION_TOKEN, sessionToken)
                        if (configManager.getCurrentConfig().enableEncryptSync) {
                            append("secure", "1")
                        }
                    },
                    urlBuilder = {
                        toUrl()
                        buildUrl("sync", "file", "push")
                    },
                )
            mapBinaryResponse(
                response = response,
                errorCode = StandardErrorCode.PUSH_CHUNK_UPLOAD_FAIL,
                contextKey = "chunkIndex=$chunkIndex pasteId=$pasteId",
            )
        }

    suspend fun completePush(
        pasteId: Long,
        sessionToken: String,
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        request(logger, exceptionHandler, request = {
            // No request body — but Ktor needs *some* body so the encrypt plugin
            // path stays a no-op (NoContent branch) when secure=1. We use an
            // empty ByteArray with application/json so the server, which never
            // reads the body for this route, is happy either way.
            pasteClient.postBinary(
                bytes = EMPTY_BODY,
                timeout = 10_000L,
                contentType = ContentType.Application.Json,
                headersBuilder = {
                    append("targetAppInstanceId", targetAppInstanceId)
                    append(PushHeaders.PASTE_ID, pasteId.toString())
                    append(PushHeaders.SESSION_TOKEN, sessionToken)
                    if (configManager.getCurrentConfig().enableEncryptSync) {
                        append("secure", "1")
                    }
                },
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "paste", "push", "complete")
                },
            )
        }) { response ->
            response.body<PushCompleteResponse>()
        }

    suspend fun pushIcon(
        source: String,
        targetAppInstanceId: String,
        iconBytes: ByteArray,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        safeApiCall(logger, exceptionHandler) {
            val response =
                pasteClient.postBinary(
                    bytes = iconBytes,
                    timeout = 30_000L,
                    headersBuilder = {
                        append("targetAppInstanceId", targetAppInstanceId)
                        if (configManager.getCurrentConfig().enableEncryptSync) {
                            append("secure", "1")
                        }
                    },
                    urlBuilder = {
                        toUrl()
                        buildUrl("sync", "icon", "push", source)
                    },
                )
            mapBinaryResponse(
                response = response,
                errorCode = StandardErrorCode.PUSH_CHUNK_UPLOAD_FAIL,
                contextKey = "icon=$source",
            )
        }

    private suspend fun mapBinaryResponse(
        response: io.ktor.client.statement.HttpResponse,
        errorCode: StandardErrorCode,
        contextKey: String,
    ): ClientApiResult =
        if (response.status.value == 200) {
            SuccessResult()
        } else {
            runCatching {
                val fail = response.body<FailResponse>()
                createFailureResult(
                    errorCode,
                    "push failed ($contextKey): ${response.status.value} $fail",
                )
            }.getOrElse {
                createFailureResult(
                    errorCode,
                    "push failed ($contextKey): ${response.status.value}",
                )
            }
        }

    companion object {
        private val EMPTY_BODY: ByteArray = ByteArray(0)
    }
}
