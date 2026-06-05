package com.crosspaste.platform.linux.api

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure

/**
 * Minimal JNA binding to libc for RTNETLINK (`NETLINK_ROUTE`) socket monitoring.
 *
 * Drives the event-driven [com.crosspaste.net.LinuxNetworkStateMonitor]: a netlink
 * socket bound to the link/address multicast groups becomes readable whenever the
 * kernel reports an interface or address change. The monitor `poll`s the socket and
 * `recv`s only when it is readable.
 *
 * Sizes assume a 64-bit (LP64) build: `size_t`/`ssize_t` are 8 bytes and map to
 * Kotlin [Long]; `socklen_t` is 4 bytes and maps to [Int]; `nfds_t` is 8 bytes and
 * maps to [Long].
 */
interface NetlinkLib : Library {

    fun socket(
        domain: Int,
        type: Int,
        protocol: Int,
    ): Int

    fun bind(
        fd: Int,
        addr: SockAddrNetlink,
        addrlen: Int,
    ): Int

    fun recv(
        fd: Int,
        buf: ByteArray,
        len: Long,
        flags: Int,
    ): Long

    /**
     * Waits up to [timeout] ms for events on [fds] (here a single socket). Returns the
     * number of ready descriptors, 0 on timeout, or < 0 on error. The bounded timeout
     * lets the monitor notice [com.crosspaste.net.LinuxNetworkStateMonitor.stop].
     */
    fun poll(
        fds: PollFd,
        nfds: Long,
        timeout: Int,
    ): Int

    fun close(fd: Int): Int

    /** `struct pollfd` (8 bytes). */
    @Structure.FieldOrder("fd", "events", "revents")
    class PollFd : Structure() {
        @JvmField var fd: Int = 0

        @JvmField var events: Short = 0

        @JvmField var revents: Short = 0
    }

    /** `struct sockaddr_nl` (12 bytes). */
    @Structure.FieldOrder("nlFamily", "nlPad", "nlPid", "nlGroups")
    class SockAddrNetlink : Structure() {
        @JvmField var nlFamily: Short = 0

        @JvmField var nlPad: Short = 0

        @JvmField var nlPid: Int = 0

        @JvmField var nlGroups: Int = 0
    }

    companion object {
        val INSTANCE: NetlinkLib = Native.load("c", NetlinkLib::class.java)

        const val AF_NETLINK = 16
        const val SOCK_RAW = 3
        const val NETLINK_ROUTE = 0

        // Multicast groups: link state, plus IPv4/IPv6 address add/remove.
        const val RTMGRP_LINK = 0x1
        const val RTMGRP_IPV4_IFADDR = 0x10
        const val RTMGRP_IPV6_IFADDR = 0x100

        // poll() events: data available to read.
        const val POLLIN: Short = 0x0001
    }
}
