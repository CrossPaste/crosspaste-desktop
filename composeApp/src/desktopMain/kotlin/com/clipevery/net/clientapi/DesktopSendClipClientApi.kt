package com.clipevery.net.clientapi

import com.clipevery.dao.clip.ClipData
import com.clipevery.net.ClipClient
import com.clipevery.presist.ClipDataFilePersistIterable
import com.clipevery.presist.OneFilePersist
import io.ktor.http.*
import io.ktor.util.reflect.*
import java.io.File

class DesktopSendClipClientApi(private val clipClient: ClipClient): SendClipClientApi {
    override suspend fun sendClip(
        clipData: ClipData,
        clipBytes: ByteArray,
        toUrl: URLBuilder.(URLBuilder) -> Unit
    ): Int {
        // todo Support file directory transfer
        val iterator: Iterator<OneFilePersist> = ClipDataFilePersistIterable(clipData).iterator()
        val files: List<File> = iterator.asSequence().map { it.path.toFile() }.toList()
        val response = clipClient.post(message = clipData,
            messageType = typeInfo<ClipData>(),
            files = files,
            timeout = 5000L,
            urlBuilder = toUrl)
        return if (response.status.value != 200) {
            SyncClipResult.FAILED
        } else {
            SyncClipResult.SUCCESS
        }
    }
}