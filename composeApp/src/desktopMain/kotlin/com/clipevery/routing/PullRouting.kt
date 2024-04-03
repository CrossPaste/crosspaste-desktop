package com.clipevery.routing

import com.clipevery.Dependencies
import com.clipevery.clip.CacheManager
import com.clipevery.dto.pull.PullFileRequest
import com.clipevery.dto.pull.PullFilesKey
import com.clipevery.exception.StandardErrorCode
import com.clipevery.sync.SyncManager
import com.clipevery.utils.FileUtils
import com.clipevery.utils.failResponse
import com.clipevery.utils.getAppInstanceId
import com.clipevery.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

fun Routing.pullRouting() {

    val logger = KotlinLogging.logger {}

    val koinApplication = Dependencies.koinApplication

    val syncManager = koinApplication.koin.get<SyncManager>()

    val cacheManager = koinApplication.koin.get<CacheManager>()

    val fileUtils = koinApplication.koin.get<FileUtils>()

    post("/pull/file") {
        getAppInstanceId(call).let { fromAppInstanceId ->
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

            cacheManager.filesIndexCache.get(PullFilesKey(appInstanceId, clipId)).let { filesIndex ->
                filesIndex.getChunk(pullFileRequest.chunkIndex)?.let { chunk ->
                    val producer: suspend ByteWriteChannel.() -> Unit = {
                        fileUtils.readFilesChunk(chunk, this)
                    }
                    successResponse(call, producer)
                } ?: run {
                    logger.error { "$fromAppInstanceId get $appInstanceId chunk out range: ${pullFileRequest.chunkIndex}" }
                    failResponse(call, StandardErrorCode.OUT_RANGE_CHUNK_INDEX.toErrorCode(),
                        "out range chunk index ${pullFileRequest.chunkIndex}")
                }
            }
        }
    }
}
