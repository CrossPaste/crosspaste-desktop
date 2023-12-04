package com.clipevery.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.clipevery.controller.SyncValidator
import com.clipevery.device.DeviceInfoFactory
import com.clipevery.model.RequestEndpointInfo
import com.clipevery.net.ClipServer
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.image.BufferedImage

class DesktopQRCodeGenerator(private val syncValidator: SyncValidator,
                             private val clipServer: ClipServer,
                             private val deviceInfoFactory: DeviceInfoFactory): QRCodeGenerator {

    private fun endpointInfo(): String {
        val salt = syncValidator.getSalt()
        val deviceInfo = deviceInfoFactory.createDeviceInfo()
        val port = clipServer.port()
        return RequestEndpointInfo(deviceInfo, port).getBase64Encode(salt)
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
}
