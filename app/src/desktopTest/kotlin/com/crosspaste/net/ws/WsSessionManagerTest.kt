package com.crosspaste.net.ws

import io.ktor.websocket.WebSocketSession
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WsSessionManagerTest {

    private fun fakeSession(): WsSession {
        val inner: WebSocketSession = mockk(relaxed = true)
        return WsSession(inner, "remote")
    }

    @Test
    fun notifySessionClosed_presentSession_invokesCallbackOnce() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            mgr.registerSession("A", fakeSession())

            mgr.notifySessionClosed("A")

            assertEquals(listOf("A"), fired)
        }

    @Test
    fun notifySessionClosed_absentSession_doesNotInvokeCallback() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }

            mgr.notifySessionClosed("missing")

            assertEquals(emptyList(), fired)
        }

    @Test
    fun notifySessionClosed_calledTwice_invokesCallbackOnce() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            mgr.registerSession("A", fakeSession())

            mgr.notifySessionClosed("A")
            mgr.notifySessionClosed("A")

            assertEquals(listOf("A"), fired)
            assertFalse(mgr.isConnected("A"))
        }

    @Test
    fun unregisterSession_doesNotInvokeCallback() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            mgr.registerSession("A", fakeSession())

            mgr.unregisterSession("A")

            assertEquals(emptyList(), fired)
        }
}
