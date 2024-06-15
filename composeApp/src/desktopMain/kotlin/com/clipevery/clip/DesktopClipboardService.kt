package com.clipevery.clip

import com.clipevery.app.AppWindowManager
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.platform.currentPlatform

fun getDesktopClipboardService(
    appWindowManager: AppWindowManager,
    clipDao: ClipDao,
    configManager: ConfigManager,
    clipConsumer: TransferableConsumer,
    clipProducer: TransferableProducer,
): AbstractClipboardService {
    val currentPlatform = currentPlatform()
    return if (currentPlatform.isMacos()) {
        MacosClipboardService(appWindowManager, clipDao, configManager, clipConsumer, clipProducer)
    } else if (currentPlatform.isWindows()) {
        WindowsClipboardService(appWindowManager, clipDao, configManager, clipConsumer, clipProducer)
    } else if (currentPlatform.isLinux()) {
        LinuxClipboardService(appWindowManager, clipDao, configManager, clipConsumer, clipProducer)
    } else {
        throw Exception("Unsupported platform: ${currentPlatform.name}")
    }
}
