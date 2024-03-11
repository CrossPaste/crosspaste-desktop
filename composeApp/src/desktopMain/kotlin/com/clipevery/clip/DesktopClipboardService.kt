package com.clipevery.clip

import com.clipevery.os.macos.MacosClipboardService
import com.clipevery.platform.currentPlatform
import com.clipevery.os.windows.WindowsClipboardService

fun getDesktopClipboardService(clipConsumer: TransferableConsumer,
                               clipProducer: TransferableProducer): ClipboardService {
    val currentPlatform = currentPlatform()
    return if (currentPlatform.isMacos()) {
        MacosClipboardService(clipConsumer, clipProducer)
    } else if (currentPlatform.isWindows()) {
        WindowsClipboardService(clipConsumer, clipProducer)
    } else {
        throw Exception("Unsupported platform: ${currentPlatform.name}")
    }
}