package com.crosspaste.platform.linux.wayland

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * Tiny libc shim — we only need the few syscalls used to ferry clipboard
 * payloads through the anonymous pipe `zwlr_data_control_offer.receive` sets up.
 */
internal interface WaylandLibC : Library {

    fun pipe(fds: IntArray): Int

    fun close(fd: Int): Int

    fun read(
        fd: Int,
        buf: Pointer,
        count: Long,
    ): Long

    companion object {
        val INSTANCE: WaylandLibC = Native.load("c", WaylandLibC::class.java)
    }
}
