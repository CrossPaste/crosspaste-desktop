package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.getPlatform
import com.crosspaste.sound.SoundService

fun getDesktopPasteboardService(
    appWindowManager: DesktopAppWindowManager,
    configManager: ConfigManager,
    currentPaste: CurrentPaste,
    notificationManager: NotificationManager,
    pasteConsumer: TransferableConsumer,
    pasteProducer: TransferableProducer,
    pasteDao: PasteDao,
    soundService: SoundService,
): AbstractPasteboardService {
    val currentPlatform = getPlatform()
    return if (currentPlatform.isMacos()) {
        MacosPasteboardService(
            appWindowManager,
            configManager,
            currentPaste,
            notificationManager,
            pasteConsumer,
            pasteProducer,
            pasteDao,
            soundService,
        )
    } else if (currentPlatform.isWindows()) {
        WindowsPasteboardService(
            appWindowManager,
            configManager,
            currentPaste,
            notificationManager,
            pasteConsumer,
            pasteProducer,
            pasteDao,
            soundService,
        )
    } else if (currentPlatform.isLinux()) {
        LinuxPasteboardService(
            appWindowManager,
            configManager,
            currentPaste,
            notificationManager,
            pasteConsumer,
            pasteProducer,
            pasteDao,
            soundService,
        )
    } else {
        throw Exception("Unsupported platform: ${currentPlatform.name}")
    }
}
