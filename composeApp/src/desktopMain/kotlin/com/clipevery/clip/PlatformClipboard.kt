package com.clipevery.clip

import com.clipevery.os.macos.MacosClipboardService
import com.clipevery.os.windows.WindowsClipboardService
import com.clipevery.platform.currentPlatform

fun getClipboard(clipConsumer: TransferableConsumer): ClipboardService {
    val platform = currentPlatform()
    return when (platform.name) {
        "Macos" -> {
            MacosClipboardService(clipConsumer)
        }
        "Windows" -> {
            WindowsClipboardService(clipConsumer)
        }
        else -> {
            throw Exception("Unknown platform: ${platform.name}")
        }
    }
}