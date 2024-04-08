package com.clipevery.utils

import com.clipevery.dao.sync.HostInfo
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object DesktopNetUtils : NetUtils {
    override fun getHostInfoList(): List<HostInfo> {
        val nets = NetworkInterface.getNetworkInterfaces()

        return buildList {
            for (netInterface in Collections.list(nets)) {
                val inetAddresses = netInterface.inetAddresses
                for (inetAddress in Collections.list(inetAddresses)) {
                    if (inetAddress.isSiteLocalAddress) {
                        add(
                            HostInfo().apply {
                                this.hostName = inetAddress.hostName
                                this.hostAddress = inetAddress.hostAddress
                            },
                        )
                    }
                }
            }
        }
    }

    override fun getEn0IPAddress(): String? {
        try {
            // 获取所有网络接口的枚举
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in Collections.list(interfaces)) {
                // 检查是否是我们感兴趣的接口（如en0）
                if (intf.name.equals("en0", ignoreCase = true)) {
                    // 获取并遍历所有的IP地址（IPv4和IPv6）
                    val addresses = intf.inetAddresses
                    for (addr in Collections.list(addresses)) {
                        // 这里我们只关注IPv4地址
                        if (addr is InetAddress && !addr.isLoopbackAddress && addr.hostAddress.indexOf(":") == -1) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }
}
