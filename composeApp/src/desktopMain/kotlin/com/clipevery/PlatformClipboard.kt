package com.clipevery

import com.clipevery.clip.ClipboardService
import com.clipevery.macos.MacosClipboardService
import com.clipevery.platform.currentPlatform
import com.clipevery.windows.WindowsClipboardService
import java.awt.datatransfer.Transferable
import java.util.function.Consumer

fun getClipboard(clipConsumer: Consumer<Transferable>): ClipboardService {
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