package com.crosspaste.notification

import com.crosspaste.i18n.GlobalCopywriter
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationManagerTest {

    private class TestNotificationManager(
        copywriter: GlobalCopywriter,
    ) : NotificationManager(copywriter) {
        val sentNotifications = mutableListOf<Message>()
        private var nextId = 0

        override fun getMessageId(): Int = nextId++

        override fun doSendNotification(message: Message) {
            sentNotifications.add(message)
        }
    }

    private fun createCopywriter(): GlobalCopywriter {
        val copywriter = mockk<GlobalCopywriter>()
        every { copywriter.getText(any(), *anyVararg()) } answers {
            firstArg<String>()
        }
        return copywriter
    }

    @Test
    fun `pushNotification adds message to list`() {
        val manager = TestNotificationManager(createCopywriter())
        val msg = Message(messageId = 1, title = "Test", messageType = MessageType.Info)
        manager.pushNotification(msg)
        assertEquals(1, manager.notificationList.value.size)
        assertEquals("Test", manager.notificationList.value[0].title)
    }

    @Test
    fun `pushNotification prepends new messages to front of list`() {
        val manager = TestNotificationManager(createCopywriter())
        manager.pushNotification(Message(messageId = 1, title = "First", messageType = MessageType.Info))
        manager.pushNotification(Message(messageId = 2, title = "Second", messageType = MessageType.Info))
        assertEquals("Second", manager.notificationList.value[0].title)
        assertEquals("First", manager.notificationList.value[1].title)
    }

    @Test
    fun `removeNotification removes specific message by id`() {
        val manager = TestNotificationManager(createCopywriter())
        manager.pushNotification(Message(messageId = 1, title = "First", messageType = MessageType.Info))
        manager.pushNotification(Message(messageId = 2, title = "Second", messageType = MessageType.Warning))
        manager.removeNotification(1)
        assertEquals(1, manager.notificationList.value.size)
        assertEquals(2, manager.notificationList.value[0].messageId)
    }

    @Test
    fun `removeNotification with non-existent id leaves list unchanged`() {
        val manager = TestNotificationManager(createCopywriter())
        manager.pushNotification(Message(messageId = 1, title = "Test", messageType = MessageType.Info))
        manager.removeNotification(999)
        assertEquals(1, manager.notificationList.value.size)
    }

    @Test
    fun `push then remove all results in empty list`() {
        val manager = TestNotificationManager(createCopywriter())
        manager.pushNotification(Message(messageId = 1, title = "A", messageType = MessageType.Info))
        manager.pushNotification(Message(messageId = 2, title = "B", messageType = MessageType.Info))
        manager.removeNotification(1)
        manager.removeNotification(2)
        assertTrue(manager.notificationList.value.isEmpty())
    }

    @Test
    fun `getMessageId returns incrementing ids`() {
        val manager = TestNotificationManager(createCopywriter())
        val id1 = manager.getMessageId()
        val id2 = manager.getMessageId()
        val id3 = manager.getMessageId()
        assertTrue(id2 > id1)
        assertTrue(id3 > id2)
    }
}
