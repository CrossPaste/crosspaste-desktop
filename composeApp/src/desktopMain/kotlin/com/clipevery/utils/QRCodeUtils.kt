package com.clipevery.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.clipevery.device.DeviceInfoFactory
import com.clipevery.model.RequestEndpointInfo
import com.clipevery.net.ClipServer
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.signal.libsignal.protocol.state.IdentityKeyStore
import java.awt.Color
import java.awt.image.BufferedImage

class DesktopQRCodeGenerator(private val clipServer: ClipServer,
                             private val deviceInfoFactory: DeviceInfoFactory,
                             private val identityKeyStore: IdentityKeyStore): QRCodeGenerator {

    private var salt: Int = 0

    private fun bindInfo(): String {
        salt = (0..Int.MAX_VALUE).random()
        val deviceInfo = deviceInfoFactory.createDeviceInfo()
        val port = clipServer.port()
        val publicKey = identityKeyStore.identityKeyPair.publicKey
        return RequestEndpointInfo(deviceInfo, port, publicKey).getBase64Encode(salt)
    }

    override fun generateQRCode(width: Int, height: Int): ImageBitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(bindInfo(), BarcodeFormat.QR_CODE, width, height)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, if (bitMatrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
            }
        }
        return image.toComposeImageBitmap()
    }

    fun getSalt(): Int {
        return salt
    }
}
