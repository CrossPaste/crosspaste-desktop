// Uses real-dispatcher runBlocking + tiny delays rather than kotlinx-coroutines-test's
// virtual-time runTest because combine(...)+collect against a mix of StateFlow and a
// SharedFlow-backed DaemonHandle doesn't drain deterministically with a single yield()
// under StandardTestDispatcher — the manager's combine collector and the FakeHandle's
// SharedFlow emissions race across continuations. runBlocking with short delays is the
// escalation path the plan allows.
package com.crosspaste.mouse

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.platform.Platform
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MouseDaemonManagerTest {

    private class FakeHandle : MouseDaemonClient.DaemonHandle {
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

    private fun sri(
        inst: String,
        host: String?,
    ) = SyncRuntimeInfo(
        appInstanceId = inst,
        appVersion = "x",
        userName = "u",
        deviceId = "d-$inst",
        deviceName = "Dev-$inst",
        platform = Platform(name = "Mac", arch = "arm64", bitMode = 64, version = "14"),
        port = 4243,
        connectHostAddress = host,
    )

    @Test
    fun `does nothing when disabled`() =
        runBlocking {
            val handle = FakeHandle()
            val store = inMemoryStore()
            val mgr = newManager(handle, store, enabled = MutableStateFlow(false))
            val job = launch { mgr.run() }
            delay(50)
            assertTrue(handle.sent.isEmpty())
            job.cancel()
        }

    @Test
    fun `sends Start with peers when enabled`() =
        runBlocking {
            val handle = FakeHandle()
            val store = inMemoryStore().apply { upsert("inst-1", Position(1920, 0)) }
            val syncs = MutableStateFlow(listOf(sri("inst-1", "192.168.1.10")))
            val mgr = newManager(handle, store, syncs = syncs, enabled = MutableStateFlow(true))
            val job = launch { mgr.run() }
            delay(50)
            handle.emit(IpcEvent.Initialized(emptyList(), 2))
            handle.emit(IpcEvent.Capabilities(2, emptyList()))
            delay(50)
            val start = handle.sent.filterIsInstance<IpcCommand.Start>().singleOrNull()
            assertTrue(start != null, "expected exactly one Start, got ${handle.sent}")
            assertEquals("192.168.1.10:4243", start.peers.single().address)
            job.cancel()
        }

    @Test
    fun `layout change triggers updateLayout`() =
        runBlocking {
            val handle = FakeHandle()
            val store = inMemoryStore().apply { upsert("inst-1", Position(1920, 0)) }
            val syncs = MutableStateFlow(listOf(sri("inst-1", "192.168.1.10")))
            val mgr = newManager(handle, store, syncs = syncs, enabled = MutableStateFlow(true))
            val job = launch { mgr.run() }
            delay(50)
            handle.emit(IpcEvent.Initialized(emptyList(), 2))
            handle.emit(IpcEvent.Capabilities(2, emptyList())) // no update_layout → stop+start
            delay(50)
            store.upsert("inst-1", Position(-1920, 0)) // user dragged peer to the left
            delay(50)
            // Stop+start fallback expected: [Start, Stop, Start]
            val starts = handle.sent.filterIsInstance<IpcCommand.Start>()
            assertEquals(2, starts.size)
            assertEquals(
                Position(-1920, 0),
                starts
                    .last()
                    .peers
                    .single()
                    .position,
            )
            job.cancel()
        }

    @Test
    fun `disable sends Stop`() =
        runBlocking {
            val handle = FakeHandle()
            val store = inMemoryStore().apply { upsert("inst-1", Position(1920, 0)) }
            val syncs = MutableStateFlow(listOf(sri("inst-1", "192.168.1.10")))
            val enabled = MutableStateFlow(true)
            val mgr = newManager(handle, store, syncs = syncs, enabled = enabled)
            val job = launch { mgr.run() }
            delay(50)
            handle.emit(IpcEvent.Initialized(emptyList(), 2))
            handle.emit(IpcEvent.Capabilities(2, emptyList()))
            delay(50)
            enabled.value = false
            delay(50)
            assertTrue(handle.sent.any { it is IpcCommand.Stop })
            job.cancel()
        }

    // --- test helpers ---
    private fun inMemoryStore(): MouseLayoutStore {
        val backing =
            object : MouseLayoutStore.Backing {
                private val flow = MutableStateFlow<Map<String, Position>>(emptyMap())

                override fun snapshot() = flow.value

                override fun set(newMap: Map<String, Position>) {
                    flow.value = newMap
                }

                override fun flow() = flow
            }
        return MouseLayoutStore(backing)
    }

    private fun newManager(
        handle: FakeHandle,
        store: MouseLayoutStore,
        syncs: MutableStateFlow<List<SyncRuntimeInfo>> = MutableStateFlow(emptyList()),
        enabled: MutableStateFlow<Boolean>,
        port: MutableStateFlow<Int> = MutableStateFlow(4243),
    ): MouseDaemonManager {
        val dao = mockk<SyncRuntimeInfoDao>()
        every { dao.getAllSyncRuntimeInfosFlow() } returns syncs
        val client = MouseDaemonClient(handle)
        return MouseDaemonManager(
            enabledFlow = enabled,
            portFlow = port,
            layoutStore = store,
            syncRuntimeInfoDao = dao,
            clientFactory = { client },
        )
    }
}
