package com.crosspaste.net.routing

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.dto.pull.PullFileRequest
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.paste.CacheManager
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

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
            val targetAppInstanceId = call.request.headers["targetAppInstanceId"]
            if (targetAppInstanceId != appInfo.appInstanceId) {
                logger.debug { "pull file targetAppInstanceId $targetAppInstanceId not match ${appInfo.appInstanceId}" }
                failResponse(call, StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
                return@let
            }
            val pullFileRequest: PullFileRequest = call.receive()

            val syncHandler =
                syncRoutingApi.getSyncHandler(fromAppInstanceId) ?: run {
                    logger.error { "not found appInstance id: $fromAppInstanceId" }
                    failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
                    return@let
                }

            if (!syncHandler.currentSyncRuntimeInfo.allowSend) {
                logger.debug { "sync handler ($fromAppInstanceId) not allow send" }
                failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_SEND_BY_USER.toErrorCode())
                return@let
            }

            val filesIndex =
                cacheManager.getFilesIndex(pullFileRequest.id) ?: run {
                    logger.error { "$fromAppInstanceId get $targetAppInstanceId filesIndex not found" }
                    failResponse(call, StandardErrorCode.NOT_FOUND_FILES_INDEX.toErrorCode())
                    return@let
                }

            val chunk =
                filesIndex.getChunk(pullFileRequest.chunkIndex) ?: run {
                    logger.error {
                        "$fromAppInstanceId get $targetAppInstanceId chunk out range: " +
                            "${pullFileRequest.chunkIndex}"
                    }
                    failResponse(
                        call,
                        StandardErrorCode.OUT_RANGE_CHUNK_INDEX.toErrorCode(),
                        "out range chunk index ${pullFileRequest.chunkIndex}",
                    )
                    return@let
                }

            logger.info { "filesIndex ${pullFileRequest.chunkIndex} $chunk" }
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

        if (source.contains('/') || source.contains('\\') || source.contains("..")) {
            logger.warn { "icon source contains invalid characters: $source" }
            failResponse(call, StandardErrorCode.NOT_FOUND_SOURCE.toErrorCode())
            return@get
        }

        val iconPath = userDataPathProvider.resolve("$source.png", AppFileType.ICON)
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

    get("/pull/paste") {
        getAppInstanceId(call)?.let { fromAppInstanceId ->
            val targetAppInstanceId = call.request.headers["targetAppInstanceId"]
            if (targetAppInstanceId != appInfo.appInstanceId) {
                logger.debug {
                    "pull paste targetAppInstanceId $targetAppInstanceId not match ${appInfo.appInstanceId}"
                }
                failResponse(call, StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
                return@let
            }

            val syncHandler =
                syncRoutingApi.getSyncHandler(fromAppInstanceId) ?: run {
                    logger.error { "not found appInstance id: $fromAppInstanceId" }
                    failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
                    return@let
                }

            if (!syncHandler.currentSyncRuntimeInfo.allowSend) {
                logger.debug { "sync handler ($fromAppInstanceId) not allow send" }
                failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_SEND_BY_USER.toErrorCode())
                return@let
            }

            val pasteData =
                pasteDao.getLatestLoadedPasteData() ?: run {
                    logger.debug { "no paste data available for $fromAppInstanceId" }
                    failResponse(call, StandardErrorCode.SYNC_PASTE_NOT_FOUND_DATA.toErrorCode())
                    return@let
                }

            if (pasteData.isFileType()) {
                cacheManager.getFilesIndex(pasteData.id)
            }

            logger.debug { "pull paste by ($fromAppInstanceId): ${pasteData.id}" }
            successResponse(call, pasteData)
        }
    }
}
