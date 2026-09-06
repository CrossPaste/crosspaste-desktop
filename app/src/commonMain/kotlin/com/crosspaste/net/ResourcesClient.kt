package com.crosspaste.net

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.decode
import kotlinx.io.Buffer
import okio.Path

interface ResourcesClient {

    suspend fun request(
        url: String,
        maxBytes: Long,
    ): Result<ClientResponse>

    suspend fun download(
        url: String,
        path: Path,
        listener: DownloadProgressListener,
    )

    fun close() {}
}

class ClientResponse internal constructor(
    private val body: ByteArray,
    private val contentType: ContentType?,
) {

    suspend fun getBody(): ByteReadChannel = ByteReadChannel(body)

    suspend fun getBodyAsText(): String {
        val source = Buffer().apply { write(body) }
        return (contentType?.charset() ?: Charsets.UTF_8).newDecoder().decode(source)
    }

    fun getContentLength(): Long = body.size.toLong()

    fun getContentType(): ContentType? = contentType
}

object ResourceRequestLimits {

    /** Small JSON, properties and checksum documents controlled by CrossPaste. */
    const val METADATA: Long = 1024L * 1024

    /** HTML parsed for a URL preview. */
    const val HTML: Long = 8L * 1024 * 1024

    /** Encoded favicon or Open Graph image bytes, before image decoding. */
    const val IMAGE: Long = 16L * 1024 * 1024
}

class ResourceResponseTooLargeException(
    val maxBytes: Long,
) : IllegalArgumentException("Resource response exceeds $maxBytes bytes")

interface DownloadProgressListener {

    fun onFailure(
        httpStatusCode: HttpStatusCode,
        throwable: Throwable?,
    )

    fun onSuccess()

    fun onProgress(
        bytesRead: Long,
        contentLength: Long?,
    )
}
