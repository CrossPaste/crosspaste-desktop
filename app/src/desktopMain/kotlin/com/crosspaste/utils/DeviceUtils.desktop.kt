package com.crosspaste.utils

import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacDeviceUtils
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32Util
import com.sun.jna.platform.win32.WinReg
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.InetAddress
import java.util.UUID

class DesktopDeviceUtils(
    platform: Platform,
) : DeviceUtils {

    private val deviceUtils =
        if (platform.isWindows()) {
            WindowsDeviceUtils
        } else if (platform.isMacos()) {
            MacosDeviceUtils
        } else if (platform.isLinux()) {
            LinuxDeviceUtils
        } else {
            throw IllegalStateException("Unknown platform: ${platform.name}")
        }

    override fun createAppInstanceId(): String = deviceUtils.createAppInstanceId()

    override fun getDeviceId(): String = deviceUtils.getDeviceId()

    override fun getDeviceName(): String = deviceUtils.getDeviceName()
}

object WindowsDeviceUtils : DeviceUtils {

    private val logger = KotlinLogging.logger {}

    // HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid is a stable per-OS-install
    // identifier present on every supported Windows version. It replaces the
    // deprecated `wmic csproduct get UUID`, which is removed on Windows 11 24H2+.
    private const val CRYPTOGRAPHY_KEY = "SOFTWARE\\Microsoft\\Cryptography"
    private const val MACHINE_GUID_VALUE = "MachineGuid"

    private fun getMachineGuid(): String? =
        runCatching {
            Advapi32Util
                .registryGetStringValue(
                    WinReg.HKEY_LOCAL_MACHINE,
                    CRYPTOGRAPHY_KEY,
                    MACHINE_GUID_VALUE,
                ).trim()
                .takeIf { it.isNotEmpty() }
        }.onFailure { e ->
            logger.warn(e) { "Failed to read MachineGuid from registry" }
        }.getOrNull()

    override fun createAppInstanceId(): String = getMachineGuid() ?: UUID.randomUUID().toString()

    override fun getDeviceId(): String = getMachineGuid() ?: "Unknown"

    override fun getDeviceName(): String = Kernel32Util.getComputerName()
}

object MacosDeviceUtils : DeviceUtils {

    override fun createAppInstanceId(): String = MacDeviceUtils.getHardwareUUID() ?: UUID.randomUUID().toString()

    override fun getDeviceId(): String = MacDeviceUtils.getHardwareUUID() ?: "Unknown"

    override fun getDeviceName(): String = MacDeviceUtils.getComputerName() ?: "Unknown"
}

object LinuxDeviceUtils : DeviceUtils {

    private fun getMachineId(): String? {
        val file = File("/etc/machine-id")
        return if (file.exists()) {
            file.readText().trim()
        } else {
            null
        }
    }

    override fun createAppInstanceId(): String = getMachineId() ?: UUID.randomUUID().toString()

    override fun getDeviceId(): String = getMachineId() ?: "Unknown"

    override fun getDeviceName(): String {
        val hostnameFile = File("/etc/hostname")
        if (hostnameFile.exists() && hostnameFile.canRead()) {
            try {
                val name = hostnameFile.readText().trim()
                if (name.isNotEmpty() && name != "localhost") {
                    return name
                }
            } catch (_: Exception) {
            }
        }

        val envName = System.getenv("HOSTNAME") ?: System.getenv("COMPUTERNAME")
        if (!envName.isNullOrBlank()) {
            return envName
        }

        try {
            val netName = InetAddress.getLocalHost().hostName
            if (!netName.isNullOrBlank() && netName != "localhost") {
                return netName
            }
        } catch (_: Exception) {
        }

        return "Linux Device"
    }
}
