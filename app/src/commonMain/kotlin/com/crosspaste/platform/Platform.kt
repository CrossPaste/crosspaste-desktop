package com.crosspaste.platform

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
@Stable
data class Platform(
    val name: String,
    val arch: String,
    val bitMode: Int,
    val version: String,
) {

    companion object {
        const val WINDOWS = "Windows"

        const val MACOS = "Macos"

        const val LINUX = "Linux"

        const val IPHONE = "iPhone"

        const val IPAD = "iPad"

        const val ANDROID = "Android"

        const val UNKNOWN_OS = "Unknown"
    }

    fun isWindows(): Boolean = name == WINDOWS

    fun isMacos(): Boolean = name == MACOS

    fun isLinux(): Boolean = name == LINUX

    fun isDesktop(): Boolean = isWindows() || isMacos() || isLinux()

    fun isIphone(): Boolean = name == IPHONE

    fun isIpad(): Boolean = name == IPAD

    fun isAndroid(): Boolean = name == ANDROID

    fun is64bit(): Boolean = bitMode == 64
}
