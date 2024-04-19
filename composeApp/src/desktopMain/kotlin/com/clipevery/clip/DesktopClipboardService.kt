package com.clipevery.clip

import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.os.macos.MacosClipboardService
import com.clipevery.os.windows.WindowsClipboardService
import com.clipevery.platform.currentPlatform

fun getDesktopClipboardService(
    clipDao: ClipDao,
    configManager: ConfigManager,
    clipConsumer: TransferableConsumer,
    clipProducer: TransferableProducer,
): ClipboardService {
    val currentPlatform = currentPlatform()
    return if (currentPlatform.isMacos()) {
        MacosClipboardService(clipDao, configManager, clipConsumer, clipProducer)
    } else if (currentPlatform.isWindows()) {
        WindowsClipboardService(clipDao, configManager, clipConsumer, clipProducer)
    } else {
        throw Exception("Unsupported platform: ${currentPlatform.name}")
    }
}
