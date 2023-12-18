package com.clipevery.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.clipevery.endpoint.EndpointInfo
import com.clipevery.endpoint.EndpointInfoFactory
import com.clipevery.net.SyncValidator
import com.clipevery.platform.Platform
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class DesktopQRCodeGenerator(private val syncValidator: SyncValidator,
                             private val endpointInfoFactory: EndpointInfoFactory): QRCodeGenerator {

    private fun endpointInfo(): String {
        val token = syncValidator.createToken()
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        return encodeEndpointInfo(endpointInfo, token)
    }

    override fun generateQRCode(width: Int, height: Int): ImageBitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(endpointInfo(), BarcodeFormat.QR_CODE, width, height)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, if (bitMatrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
            }
        }
        return image.toComposeImageBitmap()
    }

    fun getRefreshTime(): Long {
        return syncValidator.getRefreshTime()
    }

    private fun encodeEndpointInfo(endpointInfo: EndpointInfo, token: Int): String {
        val byteStream = ByteArrayOutputStream()
        val dataStream = DataOutputStream(byteStream)
        doEncodeDeviceInfo(endpointInfo, dataStream)
        val byteArray = byteStream.toByteArray()
        val size = byteArray.size
        val offset = token % size
        val byteArrayRotate = byteArray.rotate(offset)
        val saltByteStream = ByteArrayOutputStream()
        val saltDataStream = DataOutputStream(saltByteStream)
        saltDataStream.write(byteArrayRotate)
        saltDataStream.writeInt(token)
        return base64Encode(saltByteStream.toByteArray())
    }

    private fun doEncodeDeviceInfo(endpointInfo: EndpointInfo,
                                   dataOutputStream: DataOutputStream
    ) {
        dataOutputStream.writeUTF(endpointInfo.deviceId)
        dataOutputStream.writeUTF(endpointInfo.deviceName)
        encodePlatform(endpointInfo.platform, dataOutputStream)
        dataOutputStream.writeInt(endpointInfo.hostInfoList.size)
        endpointInfo.hostInfoList.forEach {
            dataOutputStream.writeUTF(it.hostName)
            dataOutputStream.writeUTF(it.hostAddress)
        }
        dataOutputStream.writeInt(endpointInfo.port)
    }

    private fun encodePlatform(platform: Platform, dataOutputStream: DataOutputStream) {
        dataOutputStream.writeUTF(platform.name)
        dataOutputStream.writeUTF(platform.arch)
        dataOutputStream.writeInt(platform.bitMode)
        dataOutputStream.writeUTF(platform.version)
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
