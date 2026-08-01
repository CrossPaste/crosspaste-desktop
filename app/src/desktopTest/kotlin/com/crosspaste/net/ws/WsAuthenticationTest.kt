package com.crosspaste.net.ws

import com.crosspaste.net.routing.isLoopbackAddress
import com.crosspaste.secure.SecureMessageProcessor
import com.crosspaste.utils.CryptographyUtils
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WsAuthenticationTest {

    @Test
    fun `authenticated envelope rejects tampering and replay`() =
        runTest {
            val firstKeys = CryptographyUtils.generateSecureKeyPair()
            val secondKeys = CryptographyUtils.generateSecureKeyPair()
            val firstProcessor =
                SecureMessageProcessor(firstKeys.cryptKeyPair.privateKey, secondKeys.cryptKeyPair.publicKey)
            val secondProcessor =
                SecureMessageProcessor(secondKeys.cryptKeyPair.privateKey, firstKeys.cryptKeyPair.publicKey)
            val sender = WsAuthenticationContext("session", "first", "second", firstProcessor)
            val receiver = WsAuthenticationContext("session", "second", "first", secondProcessor)

            val firstEnvelope = WsEnvelope(WsMessageType.HEARTBEAT)
            val firstHeader = sender.createHeader(firstEnvelope)
            assertTrue(receiver.verify(firstHeader, firstEnvelope.payload))
            assertFalse(receiver.verify(firstHeader, firstEnvelope.payload))

            val secondEnvelope = WsEnvelope(WsMessageType.PASTE_PUSH, "payload".encodeToByteArray())
            val secondHeader = sender.createHeader(secondEnvelope)
            assertFalse(receiver.verify(secondHeader, "tampered".encodeToByteArray()))
            assertTrue(receiver.verify(secondHeader, secondEnvelope.payload))
        }

    @Test
    fun `legacy websocket is limited to loopback`() {
        assertTrue(isLoopbackAddress("127.0.0.1"))
        assertTrue(isLoopbackAddress("::1"))
        assertFalse(isLoopbackAddress("localhost"))
        assertFalse(isLoopbackAddress("192.168.1.10"))
        assertFalse(isLoopbackAddress(null))
    }
}
