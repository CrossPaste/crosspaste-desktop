package com.clipevery.clip

import com.clipevery.os.macos.MacosClipboardService
import com.clipevery.platform.currentPlatform
import com.clipevery.os.windows.WindowsClipboardService

fun getDesktopClipboardService(clipConsumer: TransferableConsumer): ClipboardService {
    val currentPlatform = currentPlatform()
    return if (currentPlatform.isMacos()) {
        MacosClipboardService(clipConsumer)
    } else if (currentPlatform.isWindows()) {
        WindowsClipboardService(clipConsumer)
    } else {
        throw Exception("Unsupported platform: ${currentPlatform.name}")
    }
}