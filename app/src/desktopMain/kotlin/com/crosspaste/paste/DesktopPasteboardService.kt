package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.sound.SoundService

fun getDesktopPasteboardService(
    appWindowManager: DesktopAppWindowManager,
    configManager: CommonConfigManager,
    currentPaste: CurrentPaste,
    notificationManager: NotificationManager,
    pasteConsumer: TransferableConsumer,
    pasteProducer: TransferableProducer,
    pasteDao: PasteDao,
    platform: Platform,
    soundService: SoundService,
    sourceExclusionService: DesktopSourceExclusionService,
): AbstractPasteboardService =
    if (platform.isMacos()) {
        MacosPasteboardService(
            appWindowManager,
            configManager,
            currentPaste,
            notificationManager,
            pasteConsumer,
            pasteProducer,
            pasteDao,
            soundService,
            sourceExclusionService,
        )
    } else if (platform.isWindows()) {
        WindowsPasteboardService(
            appWindowManager,
            configManager,
            currentPaste,
            notificationManager,
            pasteConsumer,
            pasteProducer,
            pasteDao,
            platform,
            soundService,
            sourceExclusionService,
        )
    } else if (platform.isLinux()) {
        LinuxPasteboardService(
            appWindowManager,
            configManager,
            currentPaste,
            notificationManager,
            pasteConsumer,
            pasteProducer,
            pasteDao,
            soundService,
            sourceExclusionService,
        )
    } else {
        throw IllegalStateException("Unsupported platform: ${platform.name}")
    }
