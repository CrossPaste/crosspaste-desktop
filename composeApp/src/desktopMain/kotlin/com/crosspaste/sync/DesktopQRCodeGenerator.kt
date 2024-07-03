package com.crosspaste.sync

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.crosspaste.app.AppInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.endpoint.EndpointInfoFactory
import com.crosspaste.utils.QRCodeGenerator
import com.crosspaste.utils.getEncryptUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class DesktopQRCodeGenerator(
    private val appInfo: AppInfo,
    private val endpointInfoFactory: EndpointInfoFactory,
) : QRCodeGenerator {

    private val encryptUtils = getEncryptUtils()

    private fun buildQRCode(token: CharArray): String {
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        val syncInfo = SyncInfo(appInfo, endpointInfo)

        return buildQRCode(syncInfo, token)
    }

    override fun generateQRCode(
        width: Int,
        height: Int,
        token: CharArray,
    ): ImageBitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(buildQRCode(token), BarcodeFormat.QR_CODE, width, height)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, if (bitMatrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
            }
        }
        return image.toComposeImageBitmap()
    }

    private fun buildQRCode(
        syncInfo: SyncInfo,
        token: CharArray,
    ): String {
        val syncInfoJson = Json.encodeToString(syncInfo)
        val syncInfoBytes = syncInfoJson.toByteArray()
        return encodeSyncInfo(syncInfoBytes, String(token).toInt())
    }

    private fun encodeSyncInfo(
        syncInfoBytes: ByteArray,
        token: Int,
    ): String {
        val size = syncInfoBytes.size
        val offset = token % size
        val byteArrayRotate = syncInfoBytes.rotate(offset)
        val saltByteStream = ByteArrayOutputStream()
        val saltDataStream = DataOutputStream(saltByteStream)
        saltDataStream.write(byteArrayRotate)
        saltDataStream.writeInt(token)
        return encryptUtils.base64Encode(saltByteStream.toByteArray())
    }

    private fun ByteArray.rotate(offset: Int): ByteArray {
        val effectiveOffset = offset % size
        if (effectiveOffset == 0 || this.isEmpty()) {
            return this.copyOf() // 如果偏移量为0或数组为空，则直接返回原数组的副本
        }

        val result = ByteArray(this.size)
        for (i in this.indices) {
            val newPosition = (i + effectiveOffset + this.size) % this.size
            result[newPosition] = this[i]
        }
        return result
    }
}
