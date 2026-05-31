package com.crosspaste.platform.linux.wayland

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * Hand-built `wl_interface` structs for the protocols we use.
 *
 * Wayland's C codegen normally emits these from `*-protocol.c`. Since we don't
 * pull in the C scanner output, the structs are constructed at runtime in
 * native [Memory] blocks with the layout libwayland-client expects:
 *
 * ```c
 * struct wl_interface {
 *     const char *name;          // off 0,  8 bytes
 *     int version;               // off 8,  4 bytes
 *     int method_count;          // off 12, 4 bytes
 *     const wl_message *methods; // off 16, 8 bytes
 *     int event_count;           // off 24, 4 bytes
 *     /* 4 bytes padding */
 *     const wl_message *events;  // off 32, 8 bytes
 * }; // 40 bytes
 *
 * struct wl_message {
 *     const char *name;
 *     const char *signature;
 *     const wl_interface **types;
 * }; // 24 bytes
 * ```
 *
 * All [Memory] blocks are held by [WaylandInterfaces] so the JVM keeps them
 * alive for the process lifetime (same lifetime as libwayland-client itself).
 */
object WaylandInterfaces {

    private val PTR_SIZE = Native.POINTER_SIZE.toLong()
    private const val WL_INTERFACE_SIZE = 40L
    private val WL_MESSAGE_SIZE = 3L * PTR_SIZE

    private val retainedMemory: MutableList<Memory> = mutableListOf()

    // Built-in interfaces — resolved from libwayland-client.so exported symbols.
    val wlSeatInterface: Pointer = WaylandClient.interfaceSymbol("wl_seat_interface")
    val wlRegistryInterface: Pointer = WaylandClient.interfaceSymbol("wl_registry_interface")

    // Pre-allocate struct memory for our protocol's interfaces so they can
    // hold cross-references (e.g. manager.get_data_device returns a device).
    private val managerStruct: Memory = allocInterfaceStruct()
    private val deviceStruct: Memory = allocInterfaceStruct()
    private val offerStruct: Memory = allocInterfaceStruct()
    private val sourceStruct: Memory = allocInterfaceStruct()

    val zwlrDataControlManagerV1: Pointer = managerStruct
    val zwlrDataControlDeviceV1: Pointer = deviceStruct
    val zwlrDataControlOfferV1: Pointer = offerStruct
    val zwlrDataControlSourceV1: Pointer = sourceStruct

    init {
        // zwlr_data_control_manager_v1 (version 2)
        writeInterface(
            struct = managerStruct,
            name = "zwlr_data_control_manager_v1",
            version = 2,
            requests =
                listOf(
                    Message("create_data_source", "n", listOf(sourceStruct)),
                    Message("get_data_device", "no", listOf(deviceStruct, wlSeatInterface)),
                    Message("destroy", "", emptyList()),
                ),
            events = emptyList(),
        )

        // zwlr_data_control_device_v1 (version 2)
        writeInterface(
            struct = deviceStruct,
            name = "zwlr_data_control_device_v1",
            version = 2,
            requests =
                listOf(
                    Message("set_selection", "?o", listOf(sourceStruct)),
                    Message("destroy", "", emptyList()),
                    Message("set_primary_selection", "2?o", listOf(sourceStruct)),
                ),
            events =
                listOf(
                    Message("data_offer", "n", listOf(offerStruct)),
                    Message("selection", "?o", listOf(offerStruct)),
                    Message("finished", "", emptyList()),
                    Message("primary_selection", "2?o", listOf(offerStruct)),
                ),
        )

        // zwlr_data_control_offer_v1 (version 1)
        writeInterface(
            struct = offerStruct,
            name = "zwlr_data_control_offer_v1",
            version = 1,
            requests =
                listOf(
                    Message("receive", "sh", listOf(null, null)),
                    Message("destroy", "", emptyList()),
                ),
            events =
                listOf(
                    Message("offer", "s", listOf(null)),
                ),
        )

        // zwlr_data_control_source_v1 (version 1) — declared for completeness;
        // we don't issue writes from this service yet.
        writeInterface(
            struct = sourceStruct,
            name = "zwlr_data_control_source_v1",
            version = 1,
            requests =
                listOf(
                    Message("offer", "s", listOf(null)),
                    Message("destroy", "", emptyList()),
                ),
            events =
                listOf(
                    Message("send", "sh", listOf(null, null)),
                    Message("cancelled", "", emptyList()),
                ),
        )
    }

    private data class Message(
        val name: String,
        val signature: String,
        val types: List<Pointer?>,
    )

    private fun allocInterfaceStruct(): Memory =
        Memory(WL_INTERFACE_SIZE).also {
            it.clear()
            retainedMemory += it
        }

    private fun writeInterface(
        struct: Memory,
        name: String,
        version: Int,
        requests: List<Message>,
        events: List<Message>,
    ) {
        val namePtr = allocCString(name)
        val methodsPtr = allocMessages(requests)
        val eventsPtr = allocMessages(events)

        struct.setPointer(0L, namePtr)
        struct.setInt(8L, version)
        struct.setInt(12L, requests.size)
        struct.setPointer(16L, methodsPtr)
        struct.setInt(24L, events.size)
        struct.setPointer(32L, eventsPtr)
    }

    private fun allocMessages(messages: List<Message>): Pointer {
        if (messages.isEmpty()) return Pointer.NULL
        val block = Memory(WL_MESSAGE_SIZE * messages.size).also { retainedMemory += it }
        messages.forEachIndexed { i, m ->
            val base = WL_MESSAGE_SIZE * i
            val namePtr = allocCString(m.name)
            val sigPtr = allocCString(m.signature)
            val typesPtr = allocTypes(m.types)
            block.setPointer(base + 0L * PTR_SIZE, namePtr)
            block.setPointer(base + 1L * PTR_SIZE, sigPtr)
            block.setPointer(base + 2L * PTR_SIZE, typesPtr)
        }
        return block
    }

    private fun allocTypes(types: List<Pointer?>): Pointer {
        if (types.isEmpty()) return Pointer.NULL
        val block = Memory(PTR_SIZE * types.size).also { retainedMemory += it }
        types.forEachIndexed { i, p ->
            block.setPointer(PTR_SIZE * i, p ?: Pointer.NULL)
        }
        return block
    }

    private fun allocCString(value: String): Pointer {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val block = Memory(bytes.size + 1L).also { retainedMemory += it }
        block.write(0L, bytes, 0, bytes.size)
        block.setByte(bytes.size.toLong(), 0)
        return block
    }
}
