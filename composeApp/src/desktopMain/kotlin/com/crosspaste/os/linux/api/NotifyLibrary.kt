package com.crosspaste.os.linux.api

import com.crosspaste.app.AppName
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

interface NotifyLibrary : Library {
    fun notify_init(app_name: String): Boolean

    fun notify_uninit()

    fun notify_notification_new(
        summary: String,
        body: String?,
        icon: String?,
    ): Pointer

    fun notify_notification_show(
        notification: Pointer,
        error: Pointer?,
    ): Boolean

    companion object {
        val INSTANCE: NotifyLibrary = Native.load("notify", NotifyLibrary::class.java)
    }
}

object NotificationSender {
    init {
        NotifyLibrary.INSTANCE.notify_init(AppName)
    }

    fun sendNotification(
        title: String,
        message: String,
        icon: String? = null,
    ) {
        val notification = NotifyLibrary.INSTANCE.notify_notification_new(title, message, icon)
        NotifyLibrary.INSTANCE.notify_notification_show(notification, null)
    }

    fun close() {
        NotifyLibrary.INSTANCE.notify_uninit()
    }
}
