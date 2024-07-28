package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class CrossPasteTrayState {
    private val notificationChannel = Channel<Notification>(0)

    /**
     * Flow of notifications sent by [sendNotification].
     * This flow doesn't have a buffer, so all previously sent notifications will not appear in
     * this flow.
     */
    val notificationFlow: Flow<Notification>
        get() = notificationChannel.receiveAsFlow()

    /**
     * Send notification to tray. If [CrossPasteTrayState] is attached to CrossPasteTray, notification will be sent to
     * the platform. If [CrossPasteTrayState] is not attached then notification will be lost.
     */
    fun sendNotification(notification: Notification) {
        notificationChannel.trySend(notification)
    }
}
