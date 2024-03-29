package com.clipevery.routing

import com.clipevery.Dependencies
import com.clipevery.clip.CacheManager
import com.clipevery.dto.pull.PullFileRequest
import com.clipevery.exception.StandardErrorCode
import com.clipevery.utils.FileUtils
import com.clipevery.utils.failResponse
import com.clipevery.utils.getAppInstanceId
import com.clipevery.utils.successResponse
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

fun Routing.pullRouting() {

    val koinApplication = Dependencies.koinApplication

    val cacheManager = koinApplication.koin.get<CacheManager>()

    val fileUtils = koinApplication.koin.get<FileUtils>()

    post("/pull/file") {
        getAppInstanceId(call).let {
            val pullFileRequest: PullFileRequest = call.receive()
            val appInstanceId = pullFileRequest.appInstanceId
            val clipId = pullFileRequest.clipId

            cacheManager.filesIndexCache.get(PullFilesKey(appInstanceId, clipId)).let { filesIndex ->
                filesIndex.getChunk(pullFileRequest.chunkIndex)?.let { chunk ->
                    val producer: suspend ByteWriteChannel.() -> Unit = {
                        fileUtils.readFilesChunk(chunk, this)
                    }
                    successResponse(call, producer)
                } ?: failResponse(call, StandardErrorCode.OUT_RANGE_CHUNK_INDEX.toErrorCode(), "out range chunk index ${pullFileRequest.chunkIndex}")
            }
        }
    }
}

data class PullFilesKey(val appInstanceId: String,
                        val clipId: Int)