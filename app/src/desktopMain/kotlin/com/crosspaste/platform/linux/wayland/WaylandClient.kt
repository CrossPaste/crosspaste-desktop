package com.crosspaste.platform.linux.wayland

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/**
 * Minimal JNA binding for libwayland-client.so.0.
 *
 * Only exposes the surface we need for [zwlr_data_control_v1] clipboard reads:
 * display lifecycle, the synchronous & async dispatch entry points, and proxy
 * marshalling. The variadic [wl_proxy_marshal_flags] is exposed via [marshalFlags]
 * which dispatches through a [Function] handle, since the trailing args vary by opcode.
 */
interface WaylandClient : Library {

    fun wl_display_connect(name: String?): Pointer?

    fun wl_display_disconnect(display: Pointer)

    fun wl_display_dispatch(display: Pointer): Int

    fun wl_display_dispatch_pending(display: Pointer): Int

    fun wl_display_roundtrip(display: Pointer): Int

    fun wl_display_flush(display: Pointer): Int

    fun wl_display_get_fd(display: Pointer): Int

    fun wl_proxy_destroy(proxy: Pointer)

    fun wl_proxy_get_version(proxy: Pointer): Int

    fun wl_proxy_add_listener(
        proxy: Pointer,
        implementation: Pointer,
        data: Pointer?,
    ): Int

    companion object {

        const val LIB_NAME = "wayland-client"

        val INSTANCE: WaylandClient = Native.load(LIB_NAME, WaylandClient::class.java)

        private val library: NativeLibrary = NativeLibrary.getInstance(LIB_NAME)

        private val marshalFlags: Function = library.getFunction("wl_proxy_marshal_flags")

        /**
         * Call `wl_proxy_marshal_flags(proxy, opcode, interface, version, flags, ...)`.
         *
         * The trailing args are the per-message payload — their layout matches the
         * protocol XML's `<arg>` order for that opcode. New-id slots that the call
         * constructs (e.g. registry.bind, manager.get_data_device) are passed as `null`.
         */
        fun marshalFlags(
            proxy: Pointer,
            opcode: Int,
            interfaceStruct: Pointer?,
            version: Int,
            flags: Int,
            vararg args: Any?,
        ): Pointer? {
            val call = arrayOf(proxy, opcode, interfaceStruct, version, flags, *args)
            return marshalFlags.invokePointer(call)
        }

        /** Resolve an exported `wl_*_interface` global symbol from libwayland-client. */
        fun interfaceSymbol(name: String): Pointer = library.getGlobalVariableAddress(name)
    }
}
