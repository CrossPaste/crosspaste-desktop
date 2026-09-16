package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

interface NetworkInterfaceService {

    val networkInterfaces: StateFlow<List<NetworkInterfaceInfo>>

    /**
     * Every interface on the machine, sorted — what the settings picker lists,
     * as opposed to [networkInterfaces], which carries only the bound selection.
     *
     * A flow rather than a one-shot read so the picker keeps up with interfaces that
     * appear while the app is running (plugging in a cable, switching on a hotspot or
     * Windows ICS). The default emits a single snapshot; platforms wire it to their
     * network monitor.
     */
    val allNetworkInterfaces: Flow<List<NetworkInterfaceInfo>>
        get() = flow { emit(getSortedNetworkInterfaceInfo()) }

    fun getCurrentUseNetworkInterfaces(): List<NetworkInterfaceInfo> = networkInterfaces.value

    fun getAllNetworkInterfaceInfo(): List<NetworkInterfaceInfo>

    fun getSortedNetworkInterfaceInfo(): List<NetworkInterfaceInfo>

    fun getPreferredNetworkInterface(): NetworkInterfaceInfo?

    fun clearProviderCache()
}

/**
 * The local address(es) to hand a peer that reached us at [dialedHost].
 *
 * Normally that is the discovery selection narrowed to the address the peer actually
 * dialed. When the dialed address belongs to an interface *outside* the selection we
 * still answer with it: the peer just proved it can route there, so withholding the
 * address only leaves it with a device it can never connect to (#4996). A machine
 * sharing its connection has two LAN addresses but auto-select binds exactly one of
 * them, and the shared side always loses — Windows ICS pins it to 192.168.137.1, and
 * the lowest last octet sorts last.
 *
 * An empty selection means discovery is switched off; that must keep answering with
 * nothing, so the fallback is gated on having a selection at all.
 */
fun NetworkInterfaceService.advertiseHostInfo(dialedHost: String): List<HostInfo> {
    fun List<NetworkInterfaceInfo>.dialed(): List<HostInfo> =
        filter { it.hostAddress == dialedHost }.map { it.toHostInfo() }

    val selected = getCurrentUseNetworkInterfaces()
    if (selected.isEmpty()) {
        return emptyList()
    }
    return selected.dialed().ifEmpty { getAllNetworkInterfaceInfo().dialed() }
}
