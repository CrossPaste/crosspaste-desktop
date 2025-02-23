package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.platform.getPlatform
import com.crosspaste.sound.SoundService

fun getDesktopPasteboardService(
    appWindowManager: DesktopAppWindowManager,
    pasteDao: PasteDao,
    configManager: ConfigManager,
    currentPaste: CurrentPaste,
    pasteConsumer: TransferableConsumer,
    pasteProducer: TransferableProducer,
    soundService: SoundService,
): AbstractPasteboardService {
    val currentPlatform = getPlatform()
    return if (currentPlatform.isMacos()) {
        MacosPasteboardService(
            appWindowManager,
            pasteDao,
            configManager,
            currentPaste,
            pasteConsumer,
            pasteProducer,
            soundService,
        )
    } else if (currentPlatform.isWindows()) {
        WindowsPasteboardService(
            appWindowManager,
            pasteDao,
            configManager,
            currentPaste,
            pasteConsumer,
            pasteProducer,
            soundService,
        )
    } else if (currentPlatform.isLinux()) {
        LinuxPasteboardService(
            appWindowManager,
            pasteDao,
            configManager,
            currentPaste,
            pasteConsumer,
            pasteProducer,
            soundService,
        )
    } else {
        throw Exception("Unsupported platform: ${currentPlatform.name}")
    }
}
