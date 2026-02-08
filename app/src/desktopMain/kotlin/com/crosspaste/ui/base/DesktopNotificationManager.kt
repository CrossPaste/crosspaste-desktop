package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.Message
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.sound.SoundService
import java.util.concurrent.atomic.AtomicInteger

class DesktopNotificationManager(
    private val appWindowManager: DesktopAppWindowManager,
    copywriter: GlobalCopywriter,
    private val platform: Platform,
    private val soundService: SoundService,
) : NotificationManager(copywriter) {

    val idGenerator = AtomicInteger(0)

    val trayState = CrossPasteTrayState()

    override fun getMessageId(): Int = idGenerator.incrementAndGet()

    override fun doSendNotification(message: Message) {
        if (appWindowManager.getCurrentMainWindowInfo().show) {
            pushNotification(message)
        } else if (platform.isLinux()) {
            pushNotification(message)
        } else {
            notifyTray(message)
        }
        if (message.messageType == MessageType.Error) {
            soundService.errorSound()
        } else if (message.messageType == MessageType.Success) {
            soundService.successSound()
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
