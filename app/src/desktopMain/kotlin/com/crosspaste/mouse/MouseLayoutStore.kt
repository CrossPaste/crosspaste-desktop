package com.crosspaste.mouse

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Store for the per-device virtual-screen layout driving the mouse daemon.
 *
 * The store is a thin wrapper over a [Backing] adapter. The real production
 * [Backing] is implemented against [com.crosspaste.config.DesktopConfigManager]
 * in the Koin module; tests substitute a fake.
 */
class MouseLayoutStore(
    private val backing: Backing,
) {

    /**
     * Adapter interface between the store and the persistence layer.
     *
     * The store treats the layout as a `Map<deviceId, Position>`; the adapter
     * decides how to serialize it into whatever config representation the
     * platform uses (plain map, JSON-encoded strings, wrapper class, etc).
     */
    interface Backing {
        fun snapshot(): Map<String, Position>

        fun set(newMap: Map<String, Position>)

        fun flow(): MutableStateFlow<Map<String, Position>>
    }

    fun all(): Map<String, Position> = backing.snapshot()

    fun get(deviceId: String): Position? = backing.snapshot()[deviceId]

    fun upsert(
        deviceId: String,
        position: Position,
    ) {
        val next = backing.snapshot().toMutableMap().apply { put(deviceId, position) }
        backing.set(next)
    }

    fun remove(deviceId: String) {
        val next = backing.snapshot().toMutableMap().apply { remove(deviceId) }
        backing.set(next)
    }

    fun flow(): Flow<Map<String, Position>> = backing.flow()
}
