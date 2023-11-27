package com.clipevery

import com.clipevery.clip.ClipboardService
import com.clipevery.clip.TransferableConsumer
import com.clipevery.macos.MacosClipboardService
import com.clipevery.platform.currentPlatform
import com.clipevery.windows.WindowsClipboardService

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