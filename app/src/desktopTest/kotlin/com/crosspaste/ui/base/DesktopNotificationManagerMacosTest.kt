package com.crosspaste.ui.base

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.notification.Message
import com.crosspaste.notification.MessageType
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getPlatformUtils
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopNotificationManagerMacosTest {

    private val message =
        Message(messageId = 1, title = "title", message = "body", messageType = MessageType.Warning, duration = null)

    private val macos = Platform(Platform.MACOS, "arm64", 64, "15")

    private val linux = Platform(Platform.LINUX, "x86_64", 64, "6")

    private fun manager(
        platform: Platform,
        mainWindowShown: Boolean = false,
        postMacosNotification: ((Message) -> Boolean)? = null,
    ): DesktopNotificationManager {
        val appWindowManager =
            mockk<DesktopAppWindowManager> {
                every { getCurrentMainWindowInfo().show } returns mainWindowShown
            }
        return if (postMacosNotification == null) {
            DesktopNotificationManager(appWindowManager, mockk(relaxed = true), platform, mockk(relaxed = true))
        } else {
            DesktopNotificationManager(
                appWindowManager = appWindowManager,
                copywriter = mockk(relaxed = true),
                platform = platform,
                soundService = mockk(relaxed = true),
                postMacosNotification = postMacosNotification,
            )
        }
    }

    @Test
    fun `hidden window on macOS posts to Notification Center`() {
        val posted = mutableListOf<Message>()
        val manager = manager(macos) { posted.add(it) }

        manager.doSendNotification(message)

        assertEquals(listOf(message), posted)
        assertEquals(emptyList(), manager.notificationList.value)
    }

    @Test
    fun `hidden window on macOS falls back to a toast when Notification Center is unavailable`() {
        val manager = manager(macos) { false }

        manager.doSendNotification(message)

        assertEquals(listOf(message), manager.notificationList.value)
    }

    @Test
    fun `hidden window on macOS falls back to a toast when the native call throws an Error`() {
        val manager = manager(macos) { throw UnsatisfiedLinkError("MacosApi") }

        manager.doSendNotification(message)

        assertEquals(listOf(message), manager.notificationList.value)
    }

    @Test
    fun `hidden window on macOS keeps success and info out of Notification Center`() {
        val posted = mutableListOf<Message>()
        val manager = manager(macos) { posted.add(it) }
        val success = message.copy(messageType = MessageType.Success)
        val info = message.copy(messageId = 2, messageType = MessageType.Info)

        manager.doSendNotification(success)
        manager.doSendNotification(info)

        assertEquals(emptyList(), posted)
        assertEquals(listOf(info, success), manager.notificationList.value)
    }

    @Test
    fun `visible main window shows a toast without Notification Center`() {
        val posted = mutableListOf<Message>()
        val manager = manager(macos, mainWindowShown = true) { posted.add(it) }

        manager.doSendNotification(message)

        assertEquals(emptyList(), posted)
        assertEquals(listOf(message), manager.notificationList.value)
    }

    @Test
    fun `hidden window on Linux shows a toast without Notification Center`() {
        val posted = mutableListOf<Message>()
        val manager = manager(linux) { posted.add(it) }

        manager.doSendNotification(message)

        assertEquals(emptyList(), posted)
        assertEquals(listOf(message), manager.notificationList.value)
    }

    // The test JVM is a bare JDK process, as in a development run: the native call
    // must report Notification Center unavailable instead of aborting the process
    @Test
    fun `bare JVM on macOS falls back to a toast through the native path`() {
        assumeTrue(getPlatformUtils().platform.isMacos(), "Test requires macOS")
        val manager = manager(macos)

        manager.doSendNotification(message)

        assertEquals(listOf(message), manager.notificationList.value)
    }
}
