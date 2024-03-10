package com.clipevery.routing

import com.clipevery.Dependencies
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.exception.StandardErrorCode
import com.clipevery.presist.ClipDataFilePersistIterable
import com.clipevery.presist.OneFilePersist
import com.clipevery.utils.JsonUtils
import com.clipevery.utils.failResponse
import com.clipevery.utils.successResponse
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.mongodb.kbson.ObjectId
import java.util.Collections.emptyIterator

fun Routing.clipRouting() {

    val koinApplication = Dependencies.koinApplication

    val clipDao = koinApplication.koin.get<ClipDao>()

    post("/sync/clip") {
        val multipart = call.receiveMultipart()
        var iterator: Iterator<OneFilePersist> = emptyIterator()

        var id: ObjectId? = null

        try {
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "json") {
                            val clipData: ClipData = JsonUtils.JSON.decodeFromString(part.value)
                            id = clipDao.createClipData(clipData)
                            iterator = ClipDataFilePersistIterable(clipData).iterator()
                        }
                    }

                    is PartData.BinaryChannelItem -> {
                        if (iterator.hasNext()) {
                            val oneFilePersist = iterator.next()
                            val byteReadChannel: ByteReadChannel = part.provider()
                            oneFilePersist.writeChannel(byteReadChannel)
                        } else {
                            failResponse(
                                call, StandardErrorCode.SYNC_CLIP_NOT_FOUND_RESOURCE.toErrorCode(),
                                "not found clip file resource ${part.name}"
                            )
                        }
                    }

                    else -> {
                        // ignore
                    }
                }
                part.dispose()
            }

            successResponse(call)
        } catch (e: Exception) {
            id?.let { clipDao.markDeleteClipData(it) }
            failResponse(call, StandardErrorCode.SYNC_CLIP_ERROR.toErrorCode())
        }
    }
}