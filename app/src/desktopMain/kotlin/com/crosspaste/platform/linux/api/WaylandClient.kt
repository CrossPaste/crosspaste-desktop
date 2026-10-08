package com.crosspaste.platform.linux.api

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/**
 * Minimal JNA binding to `libwayland-client`, enough to drive a protocol whose
 * interface tables we build ourselves (see [WlInterfaceTable]).
 *
 * Only the array-based marshalling entry points are bound: the varargs ones
 * (`wl_proxy_marshal`, `wl_proxy_marshal_flags`) are what the generated C
 * headers use, but a `union wl_argument[]` built by hand is portable across
 * calling conventions where JNA varargs are not. Every symbol bound here has
 * been exported since libwayland 1.10 (2016), so any distribution that can
 * run a Wayland session at all provides it.
 *
 * Sizes assume a 64-bit (LP64) build, like the rest of the Linux bindings.
 */
interface WaylandClientLib : Library {

    fun wl_display_connect(name: String?): Pointer?

    fun wl_display_disconnect(display: Pointer)

    fun wl_display_get_fd(display: Pointer): Int

    fun wl_display_roundtrip(display: Pointer): Int

    fun wl_display_dispatch_pending(display: Pointer): Int

    fun wl_display_flush(display: Pointer): Int

    fun wl_display_prepare_read(display: Pointer): Int

    fun wl_display_read_events(display: Pointer): Int

    fun wl_display_cancel_read(display: Pointer)

    fun wl_display_get_error(display: Pointer): Int

    fun wl_proxy_marshal_array(
        proxy: Pointer,
        opcode: Int,
        args: Pointer?,
    )

    fun wl_proxy_marshal_array_constructor(
        proxy: Pointer,
        opcode: Int,
        args: Pointer?,
        `interface`: Pointer,
    ): Pointer?

    fun wl_proxy_marshal_array_constructor_versioned(
        proxy: Pointer,
        opcode: Int,
        args: Pointer?,
        `interface`: Pointer,
        version: Int,
    ): Pointer?

    fun wl_proxy_add_listener(
        proxy: Pointer,
        implementation: Pointer,
        data: Pointer?,
    ): Int

    fun wl_proxy_destroy(proxy: Pointer)

    fun wl_proxy_get_version(proxy: Pointer): Int

    companion object {

        const val LIBRARY_NAME = "wayland-client"

        /** `wl_display.get_registry` */
        const val WL_DISPLAY_GET_REGISTRY = 1

        /** `wl_registry.bind` */
        const val WL_REGISTRY_BIND = 0

        /**
         * Loaded lazily and only on demand: resolving the library on a machine
         * without `libwayland-client` (a pure X11 box) must stay a soft failure.
         */
        val INSTANCE: WaylandClientLib by lazy { Native.load(LIBRARY_NAME, WaylandClientLib::class.java) }

        private val library: NativeLibrary by lazy { NativeLibrary.getInstance(LIBRARY_NAME) }

        /** Address of the exported `struct wl_interface` data symbol [name]. */
        fun exportedInterface(name: String): Pointer = library.getGlobalVariableAddress(name)

        /**
         * Lays out a `union wl_argument[]` for [args]. Each element is 8 bytes;
         * an [Int] is written as `int32`/`uint32`/`fd`, a [String] as a pointer
         * to a NUL-terminated UTF-8 copy, a [Pointer] (or null) as an object or
         * `new_id` placeholder. The returned holder keeps the string copies
         * alive until the marshal call has returned.
         */
        fun arguments(vararg args: Any?): MarshalArguments = MarshalArguments(args.toList())
    }
}

/** Native memory backing one marshal call; see [WaylandClientLib.arguments]. */
class MarshalArguments(
    args: List<Any?>,
) {
    private val keepAlive = mutableListOf<Memory>()

    val pointer: Pointer? =
        if (args.isEmpty()) {
            null
        } else {
            Memory((args.size * ARGUMENT_SIZE).toLong()).also { memory ->
                memory.clear()
                args.forEachIndexed { index, arg ->
                    val offset = (index * ARGUMENT_SIZE).toLong()
                    when (arg) {
                        null -> memory.setPointer(offset, null)
                        is Int -> memory.setInt(offset, arg)
                        is Pointer -> memory.setPointer(offset, arg)
                        is String -> memory.setPointer(offset, nativeString(arg))
                        else -> throw IllegalArgumentException("Unsupported wl_argument: ${arg::class}")
                    }
                }
                keepAlive.add(memory)
            }
        }

    private fun nativeString(value: String): Memory {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val memory = Memory((bytes.size + 1).toLong())
        memory.write(0, bytes, 0, bytes.size)
        memory.setByte(bytes.size.toLong(), 0)
        keepAlive.add(memory)
        return memory
    }

    companion object {
        private const val ARGUMENT_SIZE = 8
    }
}

/**
 * The subset of libc needed to read a selection through a pipe and to wake
 * the event loop. `ssize_t`/`size_t`/`nfds_t` are 8 bytes (LP64) and map to
 * [Long]; everything else is a C `int`.
 */
interface WaylandLibC : Library {

    fun pipe(fds: IntArray): Int

    fun read(
        fd: Int,
        buf: ByteArray,
        count: Long,
    ): Long

    fun write(
        fd: Int,
        buf: ByteArray,
        count: Long,
    ): Long

    fun close(fd: Int): Int

    /** [fds] points at `nfds` consecutive `struct pollfd` (8 bytes each). */
    fun poll(
        fds: Pointer,
        nfds: Long,
        timeout: Int,
    ): Int

    companion object {
        val INSTANCE: WaylandLibC by lazy { Native.load("c", WaylandLibC::class.java) }

        const val POLLIN: Short = 0x0001
        const val POLLERR: Short = 0x0008
        const val POLLHUP: Short = 0x0010
        const val POLLNVAL: Short = 0x0020

        const val EINTR = 4
        const val EAGAIN = 11

        const val POLLFD_SIZE = 8L

        /** Writes `struct pollfd { fd, events, revents = 0 }` at [index] of [fds]. */
        fun setPollFd(
            fds: Pointer,
            index: Int,
            fd: Int,
            events: Short,
        ) {
            val offset = index * POLLFD_SIZE
            fds.setInt(offset, fd)
            fds.setShort(offset + 4, events)
            fds.setShort(offset + 6, 0)
        }

        fun revents(
            fds: Pointer,
            index: Int,
        ): Short = fds.getShort(index * POLLFD_SIZE + 6)
    }
}
