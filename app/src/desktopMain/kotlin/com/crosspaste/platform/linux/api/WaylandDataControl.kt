package com.crosspaste.platform.linux.api

import com.sun.jna.Memory
import com.sun.jna.Pointer

/**
 * A hand-built `struct wl_interface` with its `struct wl_message` tables, the
 * data `wayland-scanner` would otherwise generate into C. libwayland reads
 * these tables to marshal requests and to create proxies for `new_id` event
 * arguments, so the layout must match the C ABI exactly (LP64):
 *
 * ```
 * struct wl_message   { const char *name; const char *signature; const struct wl_interface **types; }  // 24 bytes
 * struct wl_interface { const char *name; int version; int method_count; const struct wl_message *methods;
 *                       int event_count; const struct wl_message *events; }                          // 40 bytes
 * ```
 *
 * All native memory is owned by the table and stays alive with it.
 */
class WlInterfaceTable(
    name: String,
    version: Int,
    methods: List<Message>,
    events: List<Message>,
) {
    /**
     * One request or event. [signature] uses the wire grammar (`u`, `i`, `s`,
     * `o`, `n`, `h`, `a`, `f`, with an optional leading since-version digit and
     * `?` for nullable). [types] carries one entry per argument: the interface
     * of an `o`/`n` argument, null for everything else (and acceptable for `o`
     * as libwayland does not type-check object arguments).
     */
    class Message(
        val name: String,
        val signature: String,
        val types: List<WlInterfaceTable?> = emptyList(),
    ) {
        init {
            val argumentCount = signature.count { it in "iufsonah" }
            require(types.size == argumentCount) {
                "$name: signature '$signature' has $argumentCount arguments but ${types.size} types"
            }
        }
    }

    private val keepAlive = mutableListOf<Memory>()

    val pointer: Pointer

    init {
        val methodTable = messageTable(methods)
        val eventTable = messageTable(events)
        val memory = Memory(INTERFACE_SIZE)
        memory.clear()
        memory.setPointer(0, nativeString(name))
        memory.setInt(8, version)
        memory.setInt(12, methods.size)
        memory.setPointer(16, methodTable)
        memory.setInt(24, events.size)
        memory.setPointer(32, eventTable)
        keepAlive.add(memory)
        pointer = memory
    }

    private fun messageTable(messages: List<Message>): Pointer? {
        if (messages.isEmpty()) return null
        val table = Memory(messages.size * MESSAGE_SIZE)
        table.clear()
        messages.forEachIndexed { index, message ->
            val offset = index * MESSAGE_SIZE
            table.setPointer(offset, nativeString(message.name))
            table.setPointer(offset + 8, nativeString(message.signature))
            table.setPointer(offset + 16, typesArray(message.types))
        }
        keepAlive.add(table)
        return table
    }

    // Always a valid array, like the scanner's shared `types[]`: libwayland
    // indexes it for every `o`/`n` argument without a null check.
    private fun typesArray(types: List<WlInterfaceTable?>): Pointer {
        val array = Memory(maxOf(types.size, 1) * POINTER_SIZE)
        array.clear()
        types.forEachIndexed { index, type ->
            array.setPointer(index * POINTER_SIZE, type?.pointer)
        }
        keepAlive.add(array)
        return array
    }

    private fun nativeString(value: String): Pointer {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val memory = Memory((bytes.size + 1).toLong())
        memory.write(0, bytes, 0, bytes.size)
        memory.setByte(bytes.size.toLong(), 0)
        keepAlive.add(memory)
        return memory
    }

    companion object {
        private const val POINTER_SIZE = 8L
        private const val MESSAGE_SIZE = 24L
        private const val INTERFACE_SIZE = 40L
    }
}

/**
 * The data-control clipboard protocol as a compositor offers it: either the
 * `wayland-protocols` staging `ext-data-control-v1` or its wlroots ancestor
 * `wlr-data-control-unstable-v1`. The two share the same shape, so one client
 * drives either; only the interface names and the opcode tables differ in
 * where `set_primary_selection` sits.
 *
 * Opcode constants below are indexes into the method/event tables and are
 * identical for both variants.
 */
class WaylandDataControlProtocol private constructor(
    /** Name of the global to bind, as `wl_registry.global` announces it. */
    val managerName: String,
    val manager: WlInterfaceTable,
    val device: WlInterfaceTable,
    val source: WlInterfaceTable,
    val offer: WlInterfaceTable,
) {
    companion object {

        /** The protocol version we bind. Selection events exist from version 1 in both variants. */
        const val BIND_VERSION = 1

        const val MANAGER_CREATE_DATA_SOURCE = 0
        const val MANAGER_GET_DATA_DEVICE = 1
        const val MANAGER_DESTROY = 2

        const val DEVICE_SET_SELECTION = 0
        const val DEVICE_DESTROY = 1

        const val DEVICE_EVENT_DATA_OFFER = 0
        const val DEVICE_EVENT_SELECTION = 1
        const val DEVICE_EVENT_FINISHED = 2
        const val DEVICE_EVENT_PRIMARY_SELECTION = 3

        const val OFFER_RECEIVE = 0
        const val OFFER_DESTROY = 1

        const val OFFER_EVENT_OFFER = 0

        val EXT: WaylandDataControlProtocol by lazy {
            build(
                prefix = "ext_data_control",
                managerVersion = 1,
                // ext v1 has primary selection from the start: no since-version prefix.
                primarySelectionSignature = "?o",
            )
        }

        val WLR: WaylandDataControlProtocol by lazy {
            build(
                prefix = "zwlr_data_control",
                managerVersion = 2,
                // wlr added primary selection in version 2.
                primarySelectionSignature = "2?o",
            )
        }

        /** Both variants, most preferred first. */
        val ALL: List<WaylandDataControlProtocol> by lazy { listOf(EXT, WLR) }

        fun byManagerName(name: String): WaylandDataControlProtocol? = ALL.firstOrNull { it.managerName == name }

        private fun build(
            prefix: String,
            managerVersion: Int,
            primarySelectionSignature: String,
        ): WaylandDataControlProtocol {
            val offer =
                WlInterfaceTable(
                    name = "${prefix}_offer_v1",
                    version = managerVersion,
                    methods =
                        listOf(
                            WlInterfaceTable.Message("receive", "sh", listOf(null, null)),
                            WlInterfaceTable.Message("destroy", ""),
                        ),
                    events = listOf(WlInterfaceTable.Message("offer", "s", listOf(null))),
                )
            val source =
                WlInterfaceTable(
                    name = "${prefix}_source_v1",
                    version = managerVersion,
                    methods =
                        listOf(
                            WlInterfaceTable.Message("offer", "s", listOf(null)),
                            WlInterfaceTable.Message("destroy", ""),
                        ),
                    events =
                        listOf(
                            WlInterfaceTable.Message("send", "sh", listOf(null, null)),
                            WlInterfaceTable.Message("cancelled", ""),
                        ),
                )
            val device =
                WlInterfaceTable(
                    name = "${prefix}_device_v1",
                    version = managerVersion,
                    methods =
                        listOf(
                            WlInterfaceTable.Message("set_selection", "?o", listOf(source)),
                            WlInterfaceTable.Message("destroy", ""),
                            WlInterfaceTable.Message(
                                "set_primary_selection",
                                primarySelectionSignature,
                                listOf(source),
                            ),
                        ),
                    events =
                        listOf(
                            WlInterfaceTable.Message("data_offer", "n", listOf(offer)),
                            WlInterfaceTable.Message("selection", "?o", listOf(offer)),
                            WlInterfaceTable.Message("finished", ""),
                            WlInterfaceTable.Message("primary_selection", primarySelectionSignature, listOf(offer)),
                        ),
                )
            val manager =
                WlInterfaceTable(
                    name = "${prefix}_manager_v1",
                    version = managerVersion,
                    methods =
                        listOf(
                            WlInterfaceTable.Message("create_data_source", "n", listOf(source)),
                            WlInterfaceTable.Message("get_data_device", "no", listOf(device, null)),
                            WlInterfaceTable.Message("destroy", ""),
                        ),
                    events = emptyList(),
                )
            return WaylandDataControlProtocol(
                managerName = "${prefix}_manager_v1",
                manager = manager,
                device = device,
                source = source,
                offer = offer,
            )
        }
    }
}
