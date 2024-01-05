package com.clipevery.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.clipevery.app.AppInfo
import com.clipevery.app.AppUI
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.endpoint.EndpointInfoFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class DesktopQRCodeGenerator(private val appUI: AppUI,
                             private val appInfo: AppInfo,
                             private val endpointInfoFactory: EndpointInfoFactory): QRCodeGenerator {

    private fun buildQRCode(): String {
        val generateToken = appUI.token
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        val syncInfo = SyncInfo(appInfo, endpointInfo)

        return buildQRCode(syncInfo, generateToken)
    }

    override fun generateQRCode(width: Int, height: Int): ImageBitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(buildQRCode(), BarcodeFormat.QR_CODE, width, height)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, if (bitMatrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
            }
        }
        return image.toComposeImageBitmap()
    }

    private fun buildQRCode(syncInfo: SyncInfo, token: CharArray): String {
        val syncInfoJson = Json.encodeToString(syncInfo)
        val syncInfoBytes = syncInfoJson.toByteArray()
        return encodeSyncInfo(syncInfoBytes, String(token).toInt())
    }

    private fun encodeSyncInfo(syncInfoBytes: ByteArray, token: Int): String {
        val size = syncInfoBytes.size
        val offset = token % size
        val byteArrayRotate = syncInfoBytes.rotate(offset)
        val saltByteStream = ByteArrayOutputStream()
        val saltDataStream = DataOutputStream(saltByteStream)
        saltDataStream.write(byteArrayRotate)
        saltDataStream.writeInt(token)
        return base64Encode(saltByteStream.toByteArray())
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
