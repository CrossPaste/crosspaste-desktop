package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.dto.pull.PullFileRequest
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.plugin.EncryptedHttpResourceLimits
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.PasteData
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.requireSyncHandler
import com.crosspaste.utils.requireTargetAppInstance
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.serialization.encodeToString
import okio.utf8Size

fun Routing.pullRouting(
    appInfo: AppInfo,
    cacheManager: CacheManager,
    pasteDao: PasteDao,
    syncRoutingApi: SyncRoutingApi,
    userDataPathProvider: UserDataPathProvider,
) {
    val logger = KotlinLogging.logger {}

    val fileUtils = getFileUtils()

    post("/pull/file") {
        getAppInstanceId(call)?.let { fromAppInstanceId ->
            if (!requireTargetAppInstance(call, appInfo)) return@let
            val pullFileRequest: PullFileRequest = call.receive()

            val syncHandler = requireSyncHandler(call, syncRoutingApi, fromAppInstanceId) ?: return@let

            if (!syncHandler.currentSyncRuntimeInfo.allowSend) {
                logger.debug { "sync handler ($fromAppInstanceId) not allow send" }
                failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_SEND_BY_USER.toErrorCode())
                return@let
            }

            val filesIndex =
                cacheManager.getFilesIndex(pullFileRequest.id) ?: run {
                    logger.error { "$fromAppInstanceId get ${appInfo.appInstanceId} filesIndex not found" }
                    failResponse(call, StandardErrorCode.NOT_FOUND_FILES_INDEX.toErrorCode())
                    return@let
                }

            val chunk =
                filesIndex.getChunk(pullFileRequest.chunkIndex) ?: run {
                    logger.error {
                        "$fromAppInstanceId get ${appInfo.appInstanceId} chunk out range: " +
                            "${pullFileRequest.chunkIndex}"
                    }
                    failResponse(
                        call,
                        StandardErrorCode.OUT_RANGE_CHUNK_INDEX.toErrorCode(),
                        "out range chunk index ${pullFileRequest.chunkIndex}",
                    )
                    return@let
                }

            logger.debug { "filesIndex ${pullFileRequest.chunkIndex} $chunk" }
            val producer: suspend ByteWriteChannel.() -> Unit = {
                fileUtils.readFilesChunk(chunk, this)
            }
            successResponse(call, producer)
        }
    }

    get("/pull/icon/{source}") {
        val source =
            call.parameters["source"] ?: run {
                logger.error { "source is null" }
                failResponse(call, StandardErrorCode.NOT_FOUND_SOURCE.toErrorCode())
                return@get
            }

        val iconPath =
            runCatching {
                userDataPathProvider.resolveIconPath(appInfo.appInstanceId, source)
            }.getOrElse {
                logger.warn { "icon source contains invalid characters: $source" }
                failResponse(call, StandardErrorCode.NOT_FOUND_SOURCE.toErrorCode())
                return@get
            }

        if (!fileUtils.existFile(iconPath)) {
            logger.error { "icon not found: $source" }
            failResponse(call, StandardErrorCode.NOT_FOUND_ICON.toErrorCode())
            return@get
        }

        val producer: suspend ByteWriteChannel.() -> Unit = {
            fileUtils.readFile(iconPath, this)
        }
        successResponse(call, producer)
    }

    get("/pull/pasteBatch") {
        getAppInstanceId(call)?.let { fromAppInstanceId ->
            if (!requireTargetAppInstance(call, appInfo)) return@let

            val syncHandler = requireSyncHandler(call, syncRoutingApi, fromAppInstanceId) ?: return@let

            if (!syncHandler.currentSyncRuntimeInfo.allowSend) {
                logger.debug { "sync handler ($fromAppInstanceId) not allow send" }
                failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_SEND_BY_USER.toErrorCode())
                return@let
            }

            val createTime = call.request.queryParameters["createTime"]?.toLongOrNull()
            val limit = (call.request.queryParameters["limit"]?.toLongOrNull() ?: 10L).coerceAtMost(50L)

            val recentPasteData =
                if (createTime != null) {
                    pasteDao.getRecentPasteDataAfterCreateTime(createTime, limit)
                } else {
                    pasteDao.getRecentPasteDataByAppInstanceId(limit)
                }
            val batch = recentPasteData.encodeJsonArrayWithinLimit()

            logger.debug {
                "pull pasteBatch by ($fromAppInstanceId): ${batch.count}/${recentPasteData.size} items"
            }
            call.respondText(batch.json, ContentType.Application.Json, HttpStatusCode.OK)
        }
    }

    get("/pull/paste") {
        getAppInstanceId(call)?.let { fromAppInstanceId ->
            if (!requireTargetAppInstance(call, appInfo)) return@let

            val syncHandler = requireSyncHandler(call, syncRoutingApi, fromAppInstanceId) ?: return@let

            if (!syncHandler.currentSyncRuntimeInfo.allowSend) {
                logger.debug { "sync handler ($fromAppInstanceId) not allow send" }
                failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_SEND_BY_USER.toErrorCode())
                return@let
            }

            val pasteData =
                pasteDao.getRecentPasteDataByAppInstanceId(1).firstOrNull() ?: run {
                    logger.debug { "no paste data available for $fromAppInstanceId" }
                    failResponse(call, StandardErrorCode.SYNC_PASTE_NOT_FOUND_DATA.toErrorCode())
                    return@let
                }

            logger.debug { "pull paste by ($fromAppInstanceId): ${pasteData.id}" }
            successResponse(call, pasteData)
        }
    }
}

internal class EncodedPasteBatch(
    val json: String,
    val count: Int,
)

/**
 * Encodes the newest-first DAO result as a JSON array, dropping trailing
 * items so the response stays within the encrypted JSON limit. One item is
 * always retained so this endpoint preserves the same single-paste
 * capability as /pull/paste.
 *
 * This endpoint has "recent N" semantics, not catch-up semantics: the DAO
 * already applies DESC + LIMIT, so older items that overflow the limit are
 * skipped by the client cursor either way. Trimming from the tail keeps the
 * same behaviour, only with a byte budget instead of a row count.
 */
internal fun List<PasteData>.encodeJsonArrayWithinLimit(
    maxBytes: Long = EncryptedHttpResourceLimits.MAX_JSON_RESPONSE_PLAINTEXT_SIZE,
): EncodedPasteBatch {
    val json = getJsonUtils().JSON
    val builder = StringBuilder("[")
    var encodedBytes = EMPTY_JSON_ARRAY_SIZE
    var count = 0

    for (pasteData in this) {
        val item = json.encodeToString(pasteData)
        val separatorBytes = if (count == 0) 0L else JSON_ITEM_SEPARATOR_SIZE
        val itemBytes = separatorBytes + item.utf8Size()
        if (count > 0 && encodedBytes + itemBytes > maxBytes) {
            break
        }
        if (count > 0) builder.append(',')
        builder.append(item)
        encodedBytes += itemBytes
        count++
    }
    builder.append(']')
    return EncodedPasteBatch(builder.toString(), count)
}

private const val EMPTY_JSON_ARRAY_SIZE = 2L
private const val JSON_ITEM_SEPARATOR_SIZE = 1L
