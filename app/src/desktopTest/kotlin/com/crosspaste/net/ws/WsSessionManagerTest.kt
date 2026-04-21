package com.crosspaste.net.ws

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WsSessionManagerTest {

    private fun fakeSession(active: Boolean = true): WsSession {
        val job = Job().apply { if (!active) cancel() }
        val inner: WebSocketSession =
            mockk(relaxed = true) {
                every { coroutineContext } returns job
            }
        return WsSession(inner, "remote")
    }

    @Test
    fun notifySessionClosed_presentSession_invokesCallbackOnce() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            val session = fakeSession()
            mgr.registerSession("A", session)

            mgr.notifySessionClosed("A", session)

            assertEquals(listOf("A"), fired)
        }

    @Test
    fun notifySessionClosed_absentSession_doesNotInvokeCallback() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }

            mgr.notifySessionClosed("missing", fakeSession())

            assertEquals(emptyList(), fired)
        }

    @Test
    fun notifySessionClosed_calledTwice_invokesCallbackOnce() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            val session = fakeSession()
            mgr.registerSession("A", session)

            mgr.notifySessionClosed("A", session)
            mgr.notifySessionClosed("A", session)

            assertEquals(listOf("A"), fired)
            assertFalse(mgr.isConnected("A"))
        }

    @Test
    fun notifySessionClosed_replacedSession_doesNotRemoveNewEntry() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            val oldSession = fakeSession()
            val newSession = fakeSession()
            mgr.registerSession("A", oldSession)
            mgr.registerSession("A", newSession)

            mgr.notifySessionClosed("A", oldSession)

            assertEquals(emptyList(), fired)
            assertTrue(mgr.isConnected("A"))
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

    @Test
    fun probe_unknownAppInstanceId_returnsFalse() =
        runTest {
            val mgr = WsSessionManager()

            assertFalse(mgr.probe("missing"))
        }

    @Test
    fun probe_sessionNotActive_firesCallbackAndReturnsFalse() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            mgr.registerSession("A", fakeSession(active = false))

            assertFalse(mgr.probe("A"))
            assertEquals(listOf("A"), fired)
            assertFalse(mgr.isConnected("A"))
        }

    @Test
    fun probe_pingFails_firesCallbackAndReturnsFalse() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            val activeJob = Job()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns activeJob
                }
            coEvery { inner.send(any<Frame.Ping>()) } throws IOException("closed")
            mgr.registerSession("A", WsSession(inner, "remote"))

            assertFalse(mgr.probe("A"))
            assertEquals(listOf("A"), fired)
            assertFalse(mgr.isConnected("A"))
        }

    @Test
    fun probe_pingSucceeds_returnsTrueAndSessionStillRegistered() =
        runTest {
            val mgr = WsSessionManager()
            val fired = mutableListOf<String>()
            mgr.setOnSessionClosed { fired.add(it) }
            val activeJob = Job()
            val inner: WebSocketSession =
                mockk(relaxed = true) {
                    every { coroutineContext } returns activeJob
                }
            coEvery { inner.send(any<Frame.Ping>()) } just Runs
            mgr.registerSession("A", WsSession(inner, "remote"))

            assertTrue(mgr.probe("A"))
            assertEquals(emptyList(), fired)
            assertTrue(mgr.isConnected("A"))
        }
}
