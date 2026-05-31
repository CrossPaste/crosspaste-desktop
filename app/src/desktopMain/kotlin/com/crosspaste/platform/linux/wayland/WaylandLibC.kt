package com.crosspaste.platform.linux.wayland

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer

/**
 * Tiny libc shim — we need the syscalls used to ferry clipboard payloads
 * through the anonymous pipe `zwlr_data_control_offer.receive` sets up, plus
 * a self-pipe + poll pair used to wake the dispatch thread on shutdown.
 */
internal interface WaylandLibC : Library {

    fun pipe(fds: IntArray): Int

    fun close(fd: Int): Int

    fun read(
        fd: Int,
        buf: Pointer,
        count: Long,
    ): Long

    fun write(
        fd: Int,
        buf: Pointer,
        count: Long,
    ): Long

    /**
     * `int poll(struct pollfd *fds, nfds_t nfds, int timeout)` — [fds] points
     * to a contiguous array of 8-byte `pollfd` entries (int fd; short events;
     * short revents). [nfds] is unsigned long on glibc; passed via [NativeLong]
     * so 64-bit Linux ABI works on both x86_64 and aarch64.
     */
    fun poll(
        fds: Pointer,
        nfds: NativeLong,
        timeoutMs: Int,
    ): Int

    companion object {
        val INSTANCE: WaylandLibC = Native.load("c", WaylandLibC::class.java)

        /** `POLLIN` from `<poll.h>`. Same value across glibc and musl. */
        const val POLLIN: Short = 0x0001
    }
}
