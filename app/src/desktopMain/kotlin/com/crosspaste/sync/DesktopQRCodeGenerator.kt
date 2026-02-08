package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.image.DesktopQRCodeImage
import com.crosspaste.image.PlatformImage
import com.crosspaste.net.NetworkInterfaceService

class DesktopQRCodeGenerator(
    appInfo: AppInfo,
    endpointInfoFactory: EndpointInfoFactory,
    networkInterfaceService: NetworkInterfaceService,
) : QRCodeGenerator(appInfo, endpointInfoFactory, networkInterfaceService) {

    override suspend fun generateQRCode(token: CharArray): PlatformImage =
        DesktopQRCodeImage(
            data = buildQRCode(token).encodeToByteArray(),
        )
}
