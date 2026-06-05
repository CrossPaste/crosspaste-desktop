package com.crosspaste.net

import com.crosspaste.platform.windows.api.Iphlpapi
import com.sun.jna.platform.win32.WinNT.HANDLEByReference
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Windows [NetworkStateMonitor] backed by the IP Helper change-notification API
 * (`NotifyIpInterfaceChange` + `NotifyUnicastIpAddressChange`).
 *
 * Event-driven, replacing the need to poll: the kernel calls back on interface
 * up/down and on unicast address add/remove (e.g. a DHCP lease change). Any failure
 * to register simply leaves the flow silent — discovery falls back to config-driven
 * behaviour rather than breaking.
 */
class WindowsNetworkStateMonitor : NetworkStateMonitor {

    private val logger = KotlinLogging.logger {}

    private val _networkChanges =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val networkChanges: Flow<Unit> = _networkChanges

    // Strong reference required: JNA does not retain the callback, so without this
    // field the native thread pool could invoke a garbage-collected trampoline.
    private val callback =
        Iphlpapi.ChangeCallback { _, _, _ ->
            _networkChanges.tryEmit(Unit)
        }

    private val interfaceHandle = HANDLEByReference()
    private val addressHandle = HANDLEByReference()

    @Volatile
    private var interfaceRegistered = false

    @Volatile
    private var addressRegistered = false

    override fun start() {
        // Interface up/down + IP interface changes.
        runCatching {
            val ret =
                Iphlpapi.INSTANCE.NotifyIpInterfaceChange(
                    Iphlpapi.AF_UNSPEC,
                    callback,
                    null,
                    false,
                    interfaceHandle,
                )
            if (ret == 0) {
                interfaceRegistered = true
            } else {
                logger.warn { "NotifyIpInterfaceChange failed: $ret" }
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to register NotifyIpInterfaceChange" }
        }

        // Unicast address add/remove (e.g. DHCP lease change).
        runCatching {
            val ret =
                Iphlpapi.INSTANCE.NotifyUnicastIpAddressChange(
                    Iphlpapi.AF_UNSPEC,
                    callback,
                    null,
                    false,
                    addressHandle,
                )
            if (ret == 0) {
                addressRegistered = true
            } else {
                logger.warn { "NotifyUnicastIpAddressChange failed: $ret" }
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to register NotifyUnicastIpAddressChange" }
        }
    }

    override fun stop() {
        if (interfaceRegistered) {
            runCatching {
                Iphlpapi.INSTANCE.CancelMibChangeNotify2(interfaceHandle.value)
            }.onFailure { e ->
                logger.warn(e) { "Failed to cancel NotifyIpInterfaceChange" }
            }
            interfaceRegistered = false
        }
        if (addressRegistered) {
            runCatching {
                Iphlpapi.INSTANCE.CancelMibChangeNotify2(addressHandle.value)
            }.onFailure { e ->
                logger.warn(e) { "Failed to cancel NotifyUnicastIpAddressChange" }
            }
            addressRegistered = false
        }
    }
}
