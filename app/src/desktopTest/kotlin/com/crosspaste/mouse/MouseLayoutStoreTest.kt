package com.crosspaste.mouse

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

    // Fake config stand-in that matches the store's expected interface.
    private class FakeConfig(
        val backing: MutableMap<String, Position>,
    ) : MouseLayoutStore.Backing {
        private val flow = MutableStateFlow(backing.toMap())

        override fun snapshot() = backing.toMap()

        override fun set(newMap: Map<String, Position>) {
            backing.clear()
            backing.putAll(newMap)
            flow.value = snapshot()
        }

        override fun flow() = flow
    }
}
