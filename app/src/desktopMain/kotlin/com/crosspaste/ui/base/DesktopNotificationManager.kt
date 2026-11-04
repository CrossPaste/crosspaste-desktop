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
import java.util.concurrent.atomic.AtomicInteger

class DesktopNotificationManager(
    private val appWindowManager: DesktopAppWindowManager,
    copywriter: GlobalCopywriter,
    private val platform: Platform,
    private val soundService: SoundService,
    // Shows a balloon on the Windows tray icon; false when it cannot be shown
    private val postWindowsNotification: (Message) -> Boolean = { message ->
        WindowsTrayNotifier(User32.INSTANCE, Shell32.INSTANCE, Kernel32.INSTANCE.GetCurrentProcessId())
            .notify(message.title, message.message ?: "", message.messageType)
    },
) : NotificationManager(copywriter) {

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
        if (!postWindowsNotification(message)) {
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
