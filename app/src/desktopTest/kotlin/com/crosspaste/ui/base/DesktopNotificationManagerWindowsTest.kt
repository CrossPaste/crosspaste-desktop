package com.crosspaste.ui.base

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.notification.Message
import com.crosspaste.notification.MessageType
import com.crosspaste.platform.Platform
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopNotificationManagerWindowsTest {

    private val message =
        Message(messageId = 1, title = "title", message = "body", messageType = MessageType.Warning, duration = null)

    private val windows = Platform(Platform.WINDOWS, "x86_64", 64, "11")

    private fun manager(
        mainWindowShown: Boolean = false,
        postWindowsNotification: (Message) -> Boolean,
    ) = DesktopNotificationManager(
        appWindowManager =
            mockk<DesktopAppWindowManager> {
                every { getCurrentMainWindowInfo().show } returns mainWindowShown
            },
        copywriter = mockk(relaxed = true),
        platform = windows,
        soundService = mockk(relaxed = true),
        postWindowsNotification = postWindowsNotification,
    )

    @Test
    fun `hidden window on Windows shows a tray balloon`() {
        val posted = mutableListOf<Message>()
        val manager = manager { posted.add(it) }

        manager.doSendNotification(message)

        assertEquals(listOf(message), posted)
        assertEquals(emptyList(), manager.notificationList.value)
    }

    @Test
    fun `hidden window on Windows falls back to a toast without a tray icon`() {
        val manager = manager { false }

        manager.doSendNotification(message)

        assertEquals(listOf(message), manager.notificationList.value)
    }

    @Test
    fun `hidden window on Windows falls back to a toast when the balloon throws an Error`() {
        val manager = manager { throw UnsatisfiedLinkError("shell32") }

        manager.doSendNotification(message)

        assertEquals(listOf(message), manager.notificationList.value)
    }

    @Test
    fun `hidden window on Windows keeps success and info out of system notifications`() {
        val posted = mutableListOf<Message>()
        val manager = manager { posted.add(it) }
        val success = message.copy(messageType = MessageType.Success)
        val info = message.copy(messageId = 2, messageType = MessageType.Info)

        manager.doSendNotification(success)
        manager.doSendNotification(info)

        assertEquals(emptyList(), posted)
        assertEquals(listOf(info, success), manager.notificationList.value)
    }

    @Test
    fun `visible main window on Windows shows a toast without a balloon`() {
        val posted = mutableListOf<Message>()
        val manager = manager(mainWindowShown = true) { posted.add(it) }

        manager.doSendNotification(message)

        assertEquals(emptyList(), posted)
        assertEquals(listOf(message), manager.notificationList.value)
    }
}
