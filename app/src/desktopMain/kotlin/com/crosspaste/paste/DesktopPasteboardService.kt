package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.platform.linux.LinuxSession
import com.crosspaste.platform.linux.wayland.WaylandClipboardSession
import com.crosspaste.sound.SoundService
import com.crosspaste.sync.SyncManager
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

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
        linuxPasteboardService(
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

private fun linuxPasteboardService(
    appWindowManager: DesktopAppWindowManager,
    configManager: CommonConfigManager,
    currentPaste: CurrentPaste,
    notificationManager: NotificationManager,
    pasteConsumer: TransferableConsumer,
    pasteProducer: TransferableProducer,
    pasteReleaseService: PasteReleaseService,
    soundService: SoundService,
    sourceExclusionService: DesktopSourceExclusionService,
): AbstractPasteboardService {
    if (LinuxSession.isWayland()) {
        val available =
            runCatching { WaylandClipboardSession.isAvailable() }
                .onFailure { e -> logger.warn(e) { "Wayland clipboard probe threw — falling back to X11" } }
                .getOrDefault(false)
        if (available) {
            logger.info { "Using LinuxWaylandPasteboardService (wlr-data-control)" }
            return LinuxWaylandPasteboardService(
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
        }
        logger.info { "Wayland session detected but wlr-data-control unavailable — using X11 fallback" }
    }
    return LinuxX11PasteboardService(
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
}
