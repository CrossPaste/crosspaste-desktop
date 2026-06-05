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
 * A dedicated daemon thread blocks on `recv`; the kernel wakes it on any interface
 * or address change (link up/down, IPv4/IPv6 address add/remove). Each wake emits a
 * change signal. [stop] closes the socket, which unblocks the `recv` and ends the
 * thread. Any failure to open/bind the socket leaves the flow silent — discovery
 * falls back to config-driven behaviour rather than breaking.
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
        while (running.get()) {
            val n = runCatching { lib.recv(socketFd, buf, buf.size.toLong(), 0) }.getOrElse { -1L }
            if (n > 0) {
                _networkChanges.tryEmit(Unit)
            } else {
                // Socket closed by stop(), or an error — exit the loop.
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
}
