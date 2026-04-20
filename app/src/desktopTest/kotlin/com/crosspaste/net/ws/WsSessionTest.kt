package com.crosspaste.net.ws

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WsSessionTest {

    private fun mockSession(
        @Suppress("UNUSED_PARAMETER") active: Boolean = true,
    ): WebSocketSession = mockk(relaxed = true)

    @Test
    fun ping_sendSucceeds_returnsTrue() =
        runTest {
            val inner = mockSession()
            coEvery { inner.send(any<Frame.Ping>()) } just Runs

            val wsSession = WsSession(inner, "remote-id")

            assertTrue(wsSession.ping())
            coVerify(exactly = 1) { inner.send(any<Frame.Ping>()) }
        }

    @Test
    fun ping_sendThrowsIOException_returnsFalse() =
        runTest {
            val inner = mockSession()
            coEvery { inner.send(any<Frame.Ping>()) } throws IOException("broken pipe")

            val wsSession = WsSession(inner, "remote-id")

            assertFalse(wsSession.ping())
        }

    @Test
    fun ping_sendThrowsCancellation_returnsFalse() =
        runTest {
            val inner = mockSession(active = false)
            coEvery { inner.send(any<Frame.Ping>()) } throws
                kotlinx.coroutines.CancellationException("session gone")

            val wsSession = WsSession(inner, "remote-id")

            assertFalse(wsSession.ping())
        }

    @Test
    fun ping_sendHangsLongerThanTimeout_returnsFalse() =
        runTest {
            val inner = mockSession()
            coEvery { inner.send(any<Frame.Ping>()) } coAnswers {
                kotlinx.coroutines.delay(10_000L)
            }

            val wsSession = WsSession(inner, "remote-id")

            assertFalse(wsSession.ping())
        }
}
