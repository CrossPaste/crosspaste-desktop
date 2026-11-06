package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.Message
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.platform.windows.WindowsTrayNotifier
import com.crosspaste.platform.windows.api.Shell32
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.sound.SoundService
import com.sun.jna.platform.win32.Kernel32
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicInteger

class DesktopNotificationManager(
    private val appWindowManager: DesktopAppWindowManager,
    copywriter: GlobalCopywriter,
    private val platform: Platform,
    private val soundService: SoundService,
    // Shows a balloon on the Windows tray icon; false when it cannot be shown
    private val postWindowsNotification: (Message) -> Boolean = defaultWindowsNotification(),
) : NotificationManager(copywriter) {

    private val logger = KotlinLogging.logger {}

    val idGenerator = AtomicInteger(0)

    val trayState = CrossPasteTrayState()

    override fun getMessageId(): Int = idGenerator.incrementAndGet()

    override fun doSendNotification(message: Message) {
        if (appWindowManager.getCurrentMainWindowInfo().show) {
            pushNotification(message)
        } else if (platform.isLinux()) {
            pushNotification(message)
        } else if (platform.isWindows()) {
            notifyWindows(message)
        } else {
            notifyTray(message)
        }
        if (message.messageType == MessageType.Error) {
            soundService.errorSound()
        } else if (message.messageType == MessageType.Success) {
            soundService.successSound()
        }
    }

    private fun notifyWindows(message: Message) {
        // runCatching also catches JNA Errors (e.g. UnsatisfiedLinkError); anything thrown here
        // would end the collector in NotificationManager and silence every later notification
        val shown =
            runCatching { postWindowsNotification(message) }
                .onFailure { logger.warn(it) { "Failed to show tray balloon" } }
                .getOrDefault(false)
        if (!shown) {
            pushNotification(message)
        }
    }

    private fun notifyTray(message: Message) {
        trayState.sendNotification(
            Notification(
                message.title,
                message.message ?: "",
                when (message.messageType) {
                    MessageType.Error -> Notification.Type.Error
                    MessageType.Info -> Notification.Type.Info
                    MessageType.Success -> Notification.Type.None
                    MessageType.Warning -> Notification.Type.Warning
                },
            ),
        )
    }
}

private fun defaultWindowsNotification(): (Message) -> Boolean {
    val notifier by lazy {
        WindowsTrayNotifier(User32.INSTANCE, Shell32.INSTANCE, Kernel32.INSTANCE.GetCurrentProcessId())
    }
    return { message -> notifier.notify(message.title, message.message ?: "", message.messageType) }
}
