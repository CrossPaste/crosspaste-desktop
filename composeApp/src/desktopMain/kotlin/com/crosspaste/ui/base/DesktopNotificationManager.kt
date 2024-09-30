package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification
import com.crosspaste.app.AppName
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.linux.api.NotificationSender.sendNotification
import com.crosspaste.sound.SoundService

class DesktopNotificationManager(
    private val appWindowManager: DesktopAppWindowManager,
    private val soundService: SoundService,
    private val toastManager: ToastManager,
) : NotificationManager {

    val platform = getPlatform()

    val trayState = CrossPasteTrayState()

    override fun addNotification(
        title: String?,
        message: String,
        messageType: MessageType,
        duration: Long,
    ) {
        if (appWindowManager.getShowMainWindow()) {
            notifyToast(message, messageType, duration)
        } else if (platform.isLinux()) {
            sendNotification(AppName, message)
        } else {
            notifyTray(title ?: AppName, message, messageType)
        }
        if (messageType == MessageType.Error) {
            soundService.errorSound()
        } else if (messageType == MessageType.Success) {
            soundService.successSound()
        }
    }

    private fun notifyToast(
        message: String,
        messageType: MessageType,
        duration: Long,
    ) {
        toastManager.setToast(
            Toast(
                message = message,
                messageType = messageType,
                duration = duration,
            ),
        )
    }

    private fun notifyTray(
        title: String,
        message: String,
        messageType: MessageType,
    ) {
        trayState.sendNotification(
            Notification(
                title,
                message,
                when (messageType) {
                    MessageType.Error -> Notification.Type.Error
                    MessageType.Info -> Notification.Type.Info
                    MessageType.Success -> Notification.Type.None
                    MessageType.Warning -> Notification.Type.Warning
                },
            ),
        )
    }
}
