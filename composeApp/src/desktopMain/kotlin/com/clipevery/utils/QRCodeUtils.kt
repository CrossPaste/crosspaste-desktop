package com.clipevery.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.clipevery.net.ClipServer
import com.clipevery.platform.currentPlatform
import com.clipevery.windows.api.User32
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.image.BufferedImage

class DesktopQRCodeGenerator(private val clipServer: ClipServer) : QRCodeGenerator {

    private var salt: Int = 0

    private fun bindInfo(): String {
        salt = (0..Int.MAX_VALUE).random()
        return clipServer.appRequestBindInfo().getBase64Encode(salt)
    }

    override fun generateQRCode(width: Int, height: Int): ImageBitmap {
        val imageWidth: Int
        val imageHeight: Int
        if (currentPlatform().isWindows()) {
            val dpiSystem = User32.INSTANCE.GetDpiForSystem()
            imageWidth = width * 96 / dpiSystem
            imageHeight = height * 96 / dpiSystem
        } else{
            imageWidth = width
            imageHeight = height
        }

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(bindInfo(), BarcodeFormat.QR_CODE, imageWidth, imageHeight)
        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until imageWidth) {
            for (y in 0 until imageHeight) {
                image.setRGB(x, y, if (bitMatrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
            }
        }
        return image.toComposeImageBitmap()
    }

    fun getSalt(): Int {
        return salt
    }
}
