package com.crosspaste.platform.windows.api

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.platform.win32.WinNT.HANDLEByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import com.sun.jna.win32.W32APIOptions

/**
 * Minimal JNA binding to the Windows IP Helper change-notification API.
 *
 * Drives the event-driven [com.crosspaste.net.WindowsNetworkStateMonitor]: the kernel
 * invokes the registered callback (on a system thread-pool thread) whenever an IP
 * interface comes up/down or a unicast address is added/removed.
 */
interface Iphlpapi : StdCallLibrary {

    /**
     * Registers for IP interface change notifications (interface up/down, metric, etc.).
     * Returns NO_ERROR (0) on success and writes the notification handle.
     */
    fun NotifyIpInterfaceChange(
        family: Int,
        callback: ChangeCallback,
        callerContext: Pointer?,
        initialNotification: Boolean,
        notificationHandle: HANDLEByReference,
    ): Int

    /**
     * Registers for unicast IP address change notifications (e.g. a DHCP lease change).
     */
    fun NotifyUnicastIpAddressChange(
        family: Int,
        callback: ChangeCallback,
        callerContext: Pointer?,
        initialNotification: Boolean,
        notificationHandle: HANDLEByReference,
    ): Int

    /** Cancels a notification registered by one of the Notify*Change calls. */
    fun CancelMibChangeNotify2(notificationHandle: HANDLE): Int

    /**
     * Shared shape for PIPINTERFACE_CHANGE_CALLBACK and PUNICAST_IPADDRESS_CHANGE_CALLBACK —
     * both are `VOID(PVOID context, PMIB_*_ROW row, MIB_NOTIFICATION_TYPE type)`. We only
     * need the "something changed" signal, so the row pointer is left opaque.
     */
    fun interface ChangeCallback : StdCallCallback {
        fun callback(
            callerContext: Pointer?,
            row: Pointer?,
            notificationType: Int,
        )
    }

    companion object {
        val INSTANCE: Iphlpapi =
            Native.load("iphlpapi", Iphlpapi::class.java, W32APIOptions.DEFAULT_OPTIONS)

        // AF_UNSPEC: notify for both IPv4 and IPv6.
        const val AF_UNSPEC = 0
    }
}
