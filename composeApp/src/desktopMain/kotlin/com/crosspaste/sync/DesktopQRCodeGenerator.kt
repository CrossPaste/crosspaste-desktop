package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.image.DesktopQRCodeImage
import com.crosspaste.image.PlatformImage

class DesktopQRCodeGenerator(
    appInfo: AppInfo,
    endpointInfoFactory: EndpointInfoFactory,
) : QRCodeGenerator(appInfo, endpointInfoFactory) {

    override fun generateQRCode(
        width: Int,
        height: Int,
        token: CharArray,
    ): PlatformImage {
        return DesktopQRCodeImage(
            data = buildQRCode(token).toByteArray(),
            width = width,
            height = height,
        )
    }
}
