package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.platform.currentPlatform

fun getDesktopPasteboardService(
    appWindowManager: DesktopAppWindowManager,
    pasteDao: PasteDao,
    configManager: ConfigManager,
    pasteConsumer: TransferableConsumer,
    pasteProducer: TransferableProducer,
): AbstractPasteboardService {
    val currentPlatform = currentPlatform()
    return if (currentPlatform.isMacos()) {
        MacosPasteboardService(appWindowManager, pasteDao, configManager, pasteConsumer, pasteProducer)
    } else if (currentPlatform.isWindows()) {
        WindowsPasteboardService(appWindowManager, pasteDao, configManager, pasteConsumer, pasteProducer)
    } else if (currentPlatform.isLinux()) {
        LinuxPasteboardService(appWindowManager, pasteDao, configManager, pasteConsumer, pasteProducer)
    } else {
        throw Exception("Unsupported platform: ${currentPlatform.name}")
    }
}
