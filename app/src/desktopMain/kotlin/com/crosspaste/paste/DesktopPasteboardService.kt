package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.sound.SoundService
import com.crosspaste.sync.SyncManager

fun getDesktopPasteboardService(
    appWindowManager: DesktopAppWindowManager,
    configManager: CommonConfigManager,
    currentPaste: CurrentPaste,
    notificationManager: NotificationManager,
    pasteConsumer: TransferableConsumer,
    pasteProducer: TransferableProducer,
    pasteReleaseService: PasteReleaseService,
    platform: Platform,
    soundService: SoundService,
    sourceExclusionService: DesktopSourceExclusionService,
    syncManager: SyncManager,
): AbstractPasteboardService =
    if (platform.isMacos()) {
        MacosPasteboardService(
            appWindowManager,
            configManager,
            currentPaste,
            notificationManager,
            pasteConsumer,
            pasteProducer,
            pasteReleaseService,
            soundService,
            sourceExclusionService,
            syncManager,
        )
    } else if (platform.isWindows()) {
        WindowsPasteboardService(
            appWindowManager,
            configManager,
            currentPaste,
            notificationManager,
            pasteConsumer,
            pasteProducer,
            pasteReleaseService,
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
            pasteReleaseService,
            soundService,
            sourceExclusionService,
        )
    } else {
        throw IllegalStateException("Unsupported platform: ${platform.name}")
    }
