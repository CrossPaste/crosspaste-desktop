package com.crosspaste.e2e.peer

import com.crosspaste.app.AppInfo
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.AbstractNetworkInterfaceService
import com.crosspaste.platform.Platform
import com.crosspaste.utils.TxtRecordUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Advertises this peer as a CrossPaste device via mDNS so the target's nearbyDeviceManager
 * sees it. Without this, target's `/sync/trust` succeeds but never writes SyncRuntimeInfo
 * (because trustSyncInfo() only registers peers it has discovered), and subsequent
 * `/sync/paste` calls fail with NOT_FOUND_APP_INSTANCE_ID.
 *
 * The peer does not run a real server — the advertised port is a placeholder. Target may
 * fail to reach back for heartbeat, but the SyncHandler stays registered, which is all
 * push scenarios need.
 */
class BonjourAdvertiser(
    private val appInfo: AppInfo,
    private val advertisedPort: Int = ADVERTISED_PORT,
) {

    companion object {
        const val SERVICE_TYPE: String = "_crosspasteService._tcp.local."

        // No real server is bound; this is a marker port baked into the announcement.
        const val ADVERTISED_PORT: Int = 13139
    }

    private val logger = KotlinLogging.logger {}

    private val instances: MutableList<JmDNS> = mutableListOf()

    fun start() {
        val addresses = enumerateLanInet4Addresses()
        if (addresses.isEmpty()) {
            logger.warn { "No usable LAN IPv4 addresses; mDNS advertising skipped." }
            return
        }
        addresses.forEach { (addr, prefix) ->
            runCatching {
                val jm = JmDNS.create(addr)
                val hostInfo =
                    HostInfo(
                        networkPrefixLength = prefix,
                        hostAddress = addr.hostAddress,
                    )
                val syncInfo =
                    SyncInfo(
                        appInfo = appInfo,
                        endpointInfo =
                            EndpointInfo(
                                deviceId = appInfo.appInstanceId,
                                deviceName = appInfo.userName,
                                platform =
                                    Platform(
                                        name = Platform.UNKNOWN_OS,
                                        arch = "x64",
                                        bitMode = 64,
                                        version = appInfo.appVersion,
                                    ),
                                hostInfoList = listOf(hostInfo),
                                port = advertisedPort,
                            ),
                    )
                val txt = TxtRecordUtils.encodeToTxtRecordDict(syncInfo)
                val info =
                    ServiceInfo.create(
                        SERVICE_TYPE,
                        "crosspaste@${appInfo.appInstanceId}@${addr.hostAddress.replace(".", "_")}",
                        advertisedPort,
                        0,
                        0,
                        txt,
                    )
                jm.registerService(info)
                instances.add(jm)
                logger.info { "Advertised mDNS service on ${addr.hostAddress}" }
            }.onFailure { e ->
                logger.warn(e) { "Failed to register mDNS on ${addr.hostAddress}" }
            }
        }
    }

    fun close() {
        instances.forEach { jm ->
            runCatching {
                jm.unregisterAllServices()
                jm.close()
            }.onFailure { e ->
                logger.debug(e) { "Error closing JmDNS instance" }
            }
        }
        instances.clear()
    }

    private fun enumerateLanInet4Addresses(): List<Pair<Inet4Address, Short>> {
        val nics =
            runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }
                .getOrElse {
                    logger.warn(it) { "Failed to enumerate network interfaces." }
                    emptyList()
                }
        return nics
            .asSequence()
            .filter { nic ->
                runCatching { nic.isUp && !nic.isLoopback && !nic.isVirtual }.getOrDefault(false)
            }.filterNot { nic ->
                AbstractNetworkInterfaceService.isLikelyVirtual(
                    nic.name,
                    AbstractNetworkInterfaceService.getMacAddress(nic),
                )
            }.flatMap { it.interfaceAddresses.asSequence() }
            .mapNotNull { ifAddr ->
                val addr = ifAddr.address
                if (addr is Inet4Address) addr to ifAddr.networkPrefixLength else null
            }.filterNot { (addr, _) ->
                addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress
            }.toList()
    }
}
