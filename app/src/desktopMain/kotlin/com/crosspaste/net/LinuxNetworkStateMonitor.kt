package com.crosspaste.net

import com.crosspaste.platform.linux.api.NetlinkLib
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Linux [NetworkStateMonitor] backed by an RTNETLINK (`NETLINK_ROUTE`) socket.
 *
 * A dedicated daemon thread `poll`s the socket; the kernel marks it readable on any
 * interface or address change (link up/down, IPv4/IPv6 address add/remove), and the
 * thread `recv`s the message and emits a change signal. [stop] flips [running] and
 * closes the socket; the poll loop observes the flag on its next timeout and exits.
 *
 * Why poll instead of a blocking `recv`: on Linux, closing the socket from another
 * thread does NOT wake a `recv` blocked on it, and netlink sockets do not support
 * `shutdown`, so a blocking `recv` could never be cancelled. The bounded poll timeout
 * is the cancellation signal. Polling an idle socket is cheap and never enumerates
 * interfaces — only a real event triggers the `recv` and the downstream re-resolve.
 *
 * Any failure to open/bind the socket leaves the flow silent — discovery falls back
 * to config-driven behaviour rather than breaking.
 *
 * Two known, deliberately-unhandled trade-offs:
 *
 *  - `poll` returning < 0 ends the loop. The only realistic cause would be `EINTR`,
 *    but a thread blocked in a native syscall is treated as safepoint-safe by the
 *    JVM (so GC/safepoints never signal it) and nothing installs a POSIX handler on
 *    this thread, so `EINTR` is effectively unreachable in normal operation. If that
 *    ever changes, retry on `EINTR` (e.g. a bounded consecutive-error counter) so a
 *    transient interrupt cannot permanently silence the monitor.
 *  - [stop] closes the socket but does NOT join this thread. That is safe ONLY
 *    because the monitor is started once at construction and never stopped during the
 *    process lifetime, so a stop→start race cannot occur. Do not rely on the
 *    `POLLNVAL`/`break` path as a guard against it: after `close`, the kernel reuses
 *    the lowest free fd, so a restart's new socket can land on the SAME fd this
 *    thread is still polling — `poll` then returns `POLLIN`, not `POLLNVAL`, and this
 *    zombie loop would steal messages from the new one. If [stop]/restart ever
 *    becomes a real code path, join the thread in [stop] before returning.
 */
class LinuxNetworkStateMonitor : NetworkStateMonitor {

    private val logger = KotlinLogging.logger {}

    private val _networkChanges =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val networkChanges: Flow<Unit> = _networkChanges

    private val running = AtomicBoolean(false)

    @Volatile
    private var fd: Int = -1

    @Volatile
    private var thread: Thread? = null

    override fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        runCatching {
            val lib = NetlinkLib.INSTANCE
            val socketFd =
                lib.socket(NetlinkLib.AF_NETLINK, NetlinkLib.SOCK_RAW, NetlinkLib.NETLINK_ROUTE)
            if (socketFd < 0) {
                logger.warn { "Failed to open RTNETLINK socket: $socketFd" }
                running.set(false)
                return
            }

            val addr =
                NetlinkLib.SockAddrNetlink().apply {
                    nlFamily = NetlinkLib.AF_NETLINK.toShort()
                    nlGroups =
                        NetlinkLib.RTMGRP_LINK or
                        NetlinkLib.RTMGRP_IPV4_IFADDR or
                        NetlinkLib.RTMGRP_IPV6_IFADDR
                }

            if (lib.bind(socketFd, addr, addr.size()) < 0) {
                logger.warn { "Failed to bind RTNETLINK socket" }
                lib.close(socketFd)
                running.set(false)
                return
            }

            fd = socketFd
            thread =
                Thread({ readLoop(lib, socketFd) }, "LinuxNetworkStateMonitor").apply {
                    isDaemon = true
                    start()
                }
        }.onFailure { e ->
            logger.warn(e) { "Failed to start RTNETLINK monitor" }
            running.set(false)
        }
    }

    private fun readLoop(
        lib: NetlinkLib,
        socketFd: Int,
    ) {
        val buf = ByteArray(8192)
        val pollFd =
            NetlinkLib.PollFd().apply {
                fd = socketFd
                events = NetlinkLib.POLLIN
            }
        while (running.get()) {
            pollFd.revents = 0
            val ready = runCatching { lib.poll(pollFd, 1L, POLL_TIMEOUT_MS) }.getOrElse { -1 }
            if (!running.get() || ready < 0) {
                break // stop() requested, or poll error.
            }
            if (ready == 0) {
                continue // Timeout: re-check running, then poll again.
            }
            if (pollFd.revents.toInt() and NetlinkLib.POLLIN.toInt() == 0) {
                break // POLLERR/POLLHUP/POLLNVAL — the socket is no longer usable.
            }
            // Readable: drain the message (we only need the "changed" signal).
            val n = runCatching { lib.recv(socketFd, buf, buf.size.toLong(), 0) }.getOrElse { -1L }
            if (n > 0) {
                _networkChanges.tryEmit(Unit)
            } else {
                break
            }
        }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        val socketFd = fd
        fd = -1
        if (socketFd >= 0) {
            runCatching {
                NetlinkLib.INSTANCE.close(socketFd)
            }.onFailure { e ->
                logger.warn(e) { "Failed to close RTNETLINK socket" }
            }
        }
        thread = null
    }

    companion object {
        // Upper bound on how long stop() waits to be observed by the poll loop.
        private const val POLL_TIMEOUT_MS = 1000
    }
}
