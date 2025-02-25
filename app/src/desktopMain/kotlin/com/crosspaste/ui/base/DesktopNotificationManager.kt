package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification
import com.crosspaste.app.AppName
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.notification.MessageObject
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.notification.ToastManager
import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.linux.api.NotificationSender.sendNotification
import com.crosspaste.sound.SoundService

class DesktopNotificationManager(
    private val appWindowManager: DesktopAppWindowManager,
    private val soundService: SoundService,
    private val toastManager: ToastManager,
) : NotificationManager() {

    val platform = getPlatform()

    val trayState = CrossPasteTrayState()

    override fun doSendNotification(messageObject: MessageObject) {
        if (appWindowManager.getShowMainWindow()) {
            notifyToast(messageObject)
        } else if (platform.isLinux()) {
            sendNotification(AppName, messageObject.message)
        } else {
            notifyTray(messageObject)
        }
        if (messageObject.messageType == MessageType.Error) {
            soundService.errorSound()
        } else if (messageObject.messageType == MessageType.Success) {
            soundService.successSound()
        }
    }

    private fun notifyToast(messageObject: MessageObject) {
        toastManager.setToast(
            Toast(
                message = messageObject.message,
                messageType = messageObject.messageType,
                duration = messageObject.duration,
            ),
        )
    }

    private fun notifyTray(messageObject: MessageObject) {
        trayState.sendNotification(
            Notification(
                messageObject.title ?: AppName,
                messageObject.message,
                when (messageObject.messageType) {
                    MessageType.Error -> Notification.Type.Error
                    MessageType.Info -> Notification.Type.Info
                    MessageType.Success -> Notification.Type.None
                    MessageType.Warning -> Notification.Type.Warning
                },
            ),
        )
    }
}
