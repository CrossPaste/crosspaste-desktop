package com.clipevery

import com.clipevery.clip.AbstractClipboard
import com.clipevery.macos.MacosClipboard
import com.clipevery.platform.currentPlatform
import com.clipevery.windows.WindowsClipboard
import java.awt.datatransfer.Transferable
import java.util.function.Consumer

fun getClipboard(clipConsumer: Consumer<Transferable>): AbstractClipboard {
    val platform = currentPlatform()
    return if (platform.name == "Macos") {
        MacosClipboard(clipConsumer)
    } else if (platform.name == "Windows") {
        WindowsClipboard(clipConsumer)
    } else {
        throw Exception("Unknown platform: ${platform.name}")
    }
}