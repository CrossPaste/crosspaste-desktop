package com.crosspaste.mouse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class MouseLayoutStoreTest {

    @Test
    fun `returns empty map when config has no entries`() {
        val cfg = FakeConfig(mutableMapOf())
        val store = MouseLayoutStore(cfg)
        assertEquals(emptyMap(), store.all())
    }

    @Test
    fun `upsert writes a single device position`() {
        val cfg = FakeConfig(mutableMapOf())
        val store = MouseLayoutStore(cfg)
        store.upsert("inst-1", Position(1920, 0))
        assertEquals(Position(1920, 0), store.get("inst-1"))
    }

    @Test
    fun `remove deletes an entry`() {
        val cfg = FakeConfig(mutableMapOf("inst-1" to Position(0, 0)))
        val store = MouseLayoutStore(cfg)
        store.remove("inst-1")
        assertEquals(emptyMap(), store.all())
    }

    @Test
    fun `flow emits after upsert`() =
        runTest {
            val cfg = FakeConfig(mutableMapOf())
            val store = MouseLayoutStore(cfg)
            val updates = mutableListOf<Map<String, Position>>()
            val job = launch { store.flow().collect { updates.add(it) } }
            yield()
            store.upsert("inst-1", Position(1920, 0))
            yield()
            job.cancel()
            assertEquals(Position(1920, 0), updates.last()["inst-1"])
        }

    @Test
    fun `concurrent upserts on distinct keys do not lose updates`() =
        runBlocking {
            // Under the previous snapshot() + set() design, concurrent upserts
            // could read the same snapshot and overwrite each other on write,
            // losing all but one update. The atomic update() contract closes
            // that race — all 200 distinct keys must survive.
            val cfg = FakeConfig(mutableMapOf())
            val store = MouseLayoutStore(cfg)
            val n = 200
            coroutineScope {
                repeat(n) { i ->
                    launch(Dispatchers.Default) {
                        store.upsert("device-$i", Position(i, 0))
                    }
                }
            }
            assertEquals(n, store.all().size)
        }

    // Fake config stand-in that matches the store's expected interface.
    private class FakeConfig(
        val backing: MutableMap<String, Position>,
    ) : MouseLayoutStore.Backing {
        private val flow = MutableStateFlow(backing.toMap())

        override fun snapshot() = backing.toMap()

        @Synchronized
        override fun update(updater: (Map<String, Position>) -> Map<String, Position>) {
            val next = updater(backing.toMap())
            backing.clear()
            backing.putAll(next)
            flow.value = next
        }

        override fun flow(): kotlinx.coroutines.flow.Flow<Map<String, Position>> = flow
    }
}
