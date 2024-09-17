package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.image.PlatformImage
import com.crosspaste.utils.getCodecsUtils
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

abstract class QRCodeGenerator(
    private val appInfo: AppInfo,
    private val endpointInfoFactory: EndpointInfoFactory,
) {

    private val codecsUtils = getCodecsUtils()

    abstract fun generateQRCode(
        width: Int,
        height: Int,
        token: CharArray,
    ): PlatformImage

    protected fun buildQRCode(token: CharArray): String {
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        val syncInfo = SyncInfo(appInfo, endpointInfo)
        return buildQRCode(syncInfo, token)
    }

    private fun buildQRCode(
        syncInfo: SyncInfo,
        token: CharArray,
    ): String {
        val syncInfoJson = Json.encodeToString(syncInfo)
        val syncInfoBytes = syncInfoJson.toByteArray()
        return encodeSyncInfo(syncInfoBytes, token.concatToString().toInt())
    }

    private fun encodeSyncInfo(
        syncInfoBytes: ByteArray,
        token: Int,
    ): String {
        val size = syncInfoBytes.size
        val offset = token % size
        val rotatedBytes = syncInfoBytes.rotate(offset)
        val saltedBytes = rotatedBytes + token.toByteArray()
        return codecsUtils.base64Encode(saltedBytes)
    }

    private fun ByteArray.rotate(offset: Int): ByteArray {
        val effectiveOffset = offset % size
        if (effectiveOffset == 0 || isEmpty()) {
            return copyOf()
        }

        return ByteArray(size) { i ->
            this[(i - effectiveOffset + size) % size]
        }
    }

    private fun Int.toByteArray(): ByteArray {
        return byteArrayOf(
            (this shr 24).toByte(),
            (this shr 16).toByte(),
            (this shr 8).toByte(),
            this.toByte(),
        )
    }
}
