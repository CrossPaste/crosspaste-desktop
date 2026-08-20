package com.crosspaste.mouse

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MouseDaemonClientTest {

    private class FakeProcess : MouseDaemonClient.DaemonHandle {
        val sent = mutableListOf<IpcCommand>()
        private val _events = MutableSharedFlow<IpcEvent>(extraBufferCapacity = 32)
        override val events: SharedFlow<IpcEvent> = _events.asSharedFlow()

        override suspend fun send(command: IpcCommand) {
            sent.add(command)
        }

        override fun close() {}

        suspend fun emit(ev: IpcEvent) {
            _events.emit(ev)
        }
    }

    @Test
    fun `negotiates capabilities on startup and records features`() =
        runTest {
            val proc = FakeProcess()
            val client = MouseDaemonClient(proc)
            val job = launch { client.run() }
            yield()
            proc.emit(IpcEvent.Initialized(screens = emptyList(), protocolVersion = 2))
            yield()
            // client should have asked for capabilities
            assertTrue(proc.sent.any { it is IpcCommand.GetCapabilities })
            proc.emit(
                IpcEvent.Capabilities(
                    protocolVersion = 2,
                    features = listOf("get_capabilities", "enumerate_local_screens"),
                ),
            )
            yield()
            assertTrue(client.capabilities.value.protocolVersion == 2)
            assertTrue("enumerate_local_screens" in client.capabilities.value.features)
            job.cancel()
        }

    @Test
    fun `updateLayout falls back to stop+start when feature unsupported`() =
        runTest {
            val proc = FakeProcess()
            val client = MouseDaemonClient(proc)
            val job = launch { client.run() }
            yield()
            proc.emit(IpcEvent.Initialized(screens = emptyList(), protocolVersion = 2))
            proc.emit(IpcEvent.Capabilities(protocolVersion = 2, features = emptyList())) // no update_layout
            yield()

            client.updateLayout(port = 4243, peers = emptyList())
            yield()

            val sentNames = proc.sent.map { it::class.simpleName }
            assertEquals(listOf("GetCapabilities", "Stop", "Start"), sentNames)
            job.cancel()
        }

    @Test
    fun `updateLayout uses native command when feature present`() =
        runTest {
            val proc = FakeProcess()
            val client = MouseDaemonClient(proc)
            val job = launch { client.run() }
            yield()
            proc.emit(IpcEvent.Initialized(screens = emptyList(), protocolVersion = 2))
            proc.emit(
                IpcEvent.Capabilities(protocolVersion = 2, features = listOf("update_layout")),
            )
            yield()

            client.updateLayout(port = 4243, peers = emptyList())
            yield()

            assertTrue(proc.sent.last() is IpcCommand.UpdateLayout)
            job.cancel()
        }

    @Test
    fun `events reach consumers as a SharedFlow`() =
        runTest {
            val proc = FakeProcess()
            val client = MouseDaemonClient(proc)
            val job = launch { client.run() }
            yield()
            val got = launch { client.events.first { it is IpcEvent.PeerConnected } }
            yield()
            proc.emit(IpcEvent.PeerConnected(name = "Desktop", deviceId = "d1"))
            got.join()
            job.cancel()
        }

    @Test
    fun `transient Stopped during updateLayout fallback is hidden from events`() =
        runTest {
            val proc = FakeProcess()
            val client = MouseDaemonClient(proc)
            val job = launch { client.run() }
            advanceUntilIdle()
            // Handshake without update_layout → fallback path
            proc.emit(IpcEvent.Initialized(screens = emptyList(), protocolVersion = 2))
            proc.emit(IpcEvent.Capabilities(protocolVersion = 2, features = emptyList()))
            advanceUntilIdle()

            val observed = mutableListOf<IpcEvent>()
            val collector = launch { client.events.collect { observed.add(it) } }
            advanceUntilIdle()

            // Trigger the stop+start fallback
            client.updateLayout(port = 4243, peers = emptyList())
            advanceUntilIdle()
            // Daemon would emit Stopped in between, then Initialized on new session.
            proc.emit(IpcEvent.Stopped)
            proc.emit(IpcEvent.Initialized(screens = emptyList(), protocolVersion = 2))
            advanceUntilIdle()

            // The transient Stopped must NOT reach observers
            assertTrue(
                observed.none { it is IpcEvent.Stopped },
                "transient Stopped leaked through: $observed",
            )
            // A subsequent real Stopped (after handshake clears the flag) should pass
            proc.emit(IpcEvent.Stopped)
            advanceUntilIdle()
            assertTrue(
                observed.any { it is IpcEvent.Stopped },
                "real Stopped after restart window was suppressed: $observed",
            )

            collector.cancel()
            job.cancel()
        }
}
