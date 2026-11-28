package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.sound.SoundService

fun getDesktopPasteboardService(
    appWindowManager: DesktopAppWindowManager,
    capturePolicy: ClipboardCapturePolicy,
    configManager: CommonConfigManager,
    currentPaste: CurrentPaste,
    notificationManager: NotificationManager,
    pasteConsumer: TransferableConsumer,
    pasteProducer: TransferableProducer,
    pasteReleaseService: PasteReleaseService,
    platform: Platform,
    soundService: SoundService,
): AbstractPasteboardService =
    if (platform.isMacos()) {
        MacosPasteboardService(
            appWindowManager,
            capturePolicy,
            configManager,
            currentPaste,
            notificationManager,
            pasteConsumer,
            pasteProducer,
            pasteReleaseService,
            soundService,
        )
    } else if (platform.isWindows()) {
        WindowsPasteboardService(
            appWindowManager,
            capturePolicy,
            configManager,
            currentPaste,
            notificationManager,
            pasteConsumer,
            pasteProducer,
            pasteReleaseService,
            platform,
            soundService,
        )
    } else if (platform.isLinux()) {
        LinuxPasteboardService(
            appWindowManager,
            capturePolicy,
            configManager,
            currentPaste,
            notificationManager,
            pasteConsumer,
            pasteProducer,
            pasteReleaseService,
            soundService,
        )
    } else {
        throw IllegalStateException("Unsupported platform: ${platform.name}")
    }
