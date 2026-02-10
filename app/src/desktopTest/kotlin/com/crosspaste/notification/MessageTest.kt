package com.crosspaste.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageTest {

    @Test
    fun `equalContent matches same type title and message`() {
        val msg1 = Message(messageId = 1, title = "Title", message = "Body", messageType = MessageType.Info)
        val msg2 = Message(messageId = 2, title = "Title", message = "Body", messageType = MessageType.Info)
        assertTrue(msg1.equalContent(msg2))
    }

    @Test
    fun `equalContent differs on different title`() {
        val msg1 = Message(messageId = 1, title = "Title1", messageType = MessageType.Info)
        val msg2 = Message(messageId = 2, title = "Title2", messageType = MessageType.Info)
        assertFalse(msg1.equalContent(msg2))
    }

    @Test
    fun `equalContent differs on different messageType`() {
        val msg1 = Message(messageId = 1, title = "Title", messageType = MessageType.Info)
        val msg2 = Message(messageId = 2, title = "Title", messageType = MessageType.Error)
        assertFalse(msg1.equalContent(msg2))
    }

    @Test
    fun `equalContent differs on different message body`() {
        val msg1 = Message(messageId = 1, title = "Title", message = "Body1", messageType = MessageType.Info)
        val msg2 = Message(messageId = 2, title = "Title", message = "Body2", messageType = MessageType.Info)
        assertFalse(msg1.equalContent(msg2))
    }

    @Test
    fun `equalContent matches when both message bodies are null`() {
        val msg1 = Message(messageId = 1, title = "Title", messageType = MessageType.Success)
        val msg2 = Message(messageId = 2, title = "Title", messageType = MessageType.Success)
        assertTrue(msg1.equalContent(msg2))
    }

    @Test
    fun `equalContent ignores messageId and duration`() {
        val msg1 = Message(messageId = 100, title = "Title", messageType = MessageType.Warning, duration = 1000)
        val msg2 = Message(messageId = 200, title = "Title", messageType = MessageType.Warning, duration = 5000)
        assertTrue(msg1.equalContent(msg2))
    }

    @Test
    fun `MessageType getMessageStyle maps each type correctly`() {
        assertEquals(MessageStyle.Error, MessageType.Error.getMessageStyle())
        assertEquals(MessageStyle.Info, MessageType.Info.getMessageStyle())
        assertEquals(MessageStyle.Success, MessageType.Success.getMessageStyle())
        assertEquals(MessageStyle.Warning, MessageType.Warning.getMessageStyle())
    }
}
