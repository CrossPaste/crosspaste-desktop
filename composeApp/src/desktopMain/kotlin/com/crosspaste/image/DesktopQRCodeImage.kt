package com.crosspaste.image

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.image.BufferedImage

class DesktopQRCodeImage(data: ByteArray, width: Int, height: Int) :
    PlatformImage(data, width, height) {

    private val writer = QRCodeWriter()

    override fun toImage(): Any {
        val bitMatrix = writer.encode(data.decodeToString(), BarcodeFormat.QR_CODE, width, height)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, if (bitMatrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
            }
        }
        return image.toComposeImageBitmap()
    }
}
