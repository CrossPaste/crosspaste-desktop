package com.crosspaste.routing

import com.crosspaste.CrossPaste
import com.crosspaste.app.AppFileType
import com.crosspaste.clip.CacheManager
import com.crosspaste.dto.pull.PullFileRequest
import com.crosspaste.dto.pull.PullFilesKey
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.path.PathProvider
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlin.io.path.exists

fun Routing.pullRouting() {
    val logger = KotlinLogging.logger {}

    val koinApplication = CrossPaste.koinApplication

    val syncManager = koinApplication.koin.get<SyncManager>()

    val cacheManager = koinApplication.koin.get<CacheManager>()

    val pathProvider = koinApplication.koin.get<PathProvider>()

    val fileUtils = getFileUtils()

    post("/pull/file") {
        getAppInstanceId(call)?.let { fromAppInstanceId ->
            val pullFileRequest: PullFileRequest = call.receive()
            val appInstanceId = pullFileRequest.appInstanceId
            val clipId = pullFileRequest.clipId

            syncManager.getSyncHandlers()[fromAppInstanceId]?.let {
                if (!it.syncRuntimeInfo.allowSend) {
                    logger.debug { "sync handler ($fromAppInstanceId) not allow send" }
                    failResponse(call, StandardErrorCode.SYNC_NOT_ALLOW_SEND.toErrorCode())
                    return@post
                }
            } ?: run {
                logger.error { "not found appInstance id: $fromAppInstanceId" }
                failResponse(call, StandardErrorCode.NOT_FOUND_APP_INSTANCE_ID.toErrorCode())
                return@post
            }

            cacheManager.getFilesIndex(PullFilesKey(appInstanceId, clipId))?.let { filesIndex ->
                filesIndex.getChunk(pullFileRequest.chunkIndex)?.let { chunk ->
                    logger.info { "filesIndex ${pullFileRequest.chunkIndex} $chunk" }
                    val producer: suspend ByteWriteChannel.() -> Unit = {
                        fileUtils.readFilesChunk(chunk, this)
                    }
                    successResponse(call, producer)
                } ?: run {
                    logger.error { "$fromAppInstanceId get $appInstanceId chunk out range: ${pullFileRequest.chunkIndex}" }
                    failResponse(
                        call, StandardErrorCode.OUT_RANGE_CHUNK_INDEX.toErrorCode(),
                        "out range chunk index ${pullFileRequest.chunkIndex}",
                    )
                }
            } ?: run {
                logger.error { "$fromAppInstanceId get $appInstanceId filesIndex not found" }
                failResponse(call, StandardErrorCode.NOT_FOUND_FILES_INDEX.toErrorCode())
            }
        }
    }

    get("/pull/icon/{source}") {
        val source =
            call.parameters["source"] ?: run {
                logger.error { "source is null" }
                failResponse(call, StandardErrorCode.NOT_FOUND_SOURCE.toErrorCode())
                return@get
            }

        val iconPath = pathProvider.resolve("$source.png", AppFileType.ICON)
        if (!iconPath.exists()) {
            logger.error { "icon not found: $source" }
            failResponse(call, StandardErrorCode.NOT_FOUND_ICON.toErrorCode())
            return@get
        }

        val producer: suspend ByteWriteChannel.() -> Unit = {
            fileUtils.readFile(iconPath, this)
        }
        successResponse(call, producer)
    }
}
