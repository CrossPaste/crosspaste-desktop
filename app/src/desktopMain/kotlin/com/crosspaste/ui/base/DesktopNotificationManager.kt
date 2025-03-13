package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.Message
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.notification.ToastManager
import com.crosspaste.platform.getPlatform
import com.crosspaste.sound.SoundService
import java.util.concurrent.atomic.AtomicInteger

class DesktopNotificationManager(
    private val appWindowManager: DesktopAppWindowManager,
    private val copywriter: GlobalCopywriter,
    private val soundService: SoundService,
    private val toastManager: ToastManager,
) : NotificationManager() {

    val idGenerator = AtomicInteger(0)

    val platform = getPlatform()

    val trayState = CrossPasteTrayState()

    override fun getMessageId(): Int {
        return idGenerator.incrementAndGet()
    }

    override fun doSendNotification(message: Message) {
        if (appWindowManager.getShowMainWindow()) {
            notifyToast(message)
        } else if (platform.isLinux()) {
            sendNotification(message)
        } else {
            notifyTray(message)
        }
        if (message.messageType == MessageType.Error) {
            soundService.errorSound()
        } else if (message.messageType == MessageType.Success) {
            soundService.successSound()
        }
    }

    private fun notifyToast(message: Message) {
        toastManager.pushToast(message)
    }

    private fun notifyTray(message: Message) {
        trayState.sendNotification(
            Notification(
                message.title(copywriter),
                message.message?.let { it(copywriter) } ?: "",
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
