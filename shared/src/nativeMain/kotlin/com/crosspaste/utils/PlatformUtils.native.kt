package com.crosspaste.utils

import com.crosspaste.platform.Platform
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CpuArchitecture
import kotlin.native.OsFamily

actual fun getPlatformUtils(): PlatformUtils = NativePlatformUtils

object NativePlatformUtils : PlatformUtils {

    override val platform: Platform by lazy { getCurrentPlatform() }

    @OptIn(ExperimentalNativeApi::class)
    private fun getCurrentPlatform(): Platform {
        val osFamily = kotlin.native.Platform.osFamily
        val cpuArch = kotlin.native.Platform.cpuArchitecture

        val name =
            when (osFamily) {
                OsFamily.MACOSX -> Platform.MACOS
                OsFamily.LINUX -> Platform.LINUX
                OsFamily.WINDOWS -> Platform.WINDOWS
                else -> Platform.UNKNOWN_OS
            }

        val arch =
            when (cpuArch) {
                CpuArchitecture.ARM64 -> "aarch64"
                CpuArchitecture.X64 -> "x86_64"
                CpuArchitecture.X86 -> "x86"
                else -> cpuArch.name
            }

        val bitMode =
            if (cpuArch == CpuArchitecture.ARM64 || cpuArch == CpuArchitecture.X64) 64 else 32

        return Platform(name = name, arch = arch, bitMode = bitMode, version = "")
    }
}
