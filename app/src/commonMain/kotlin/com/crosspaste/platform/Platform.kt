package com.crosspaste.platform

import kotlinx.serialization.Serializable

expect fun getPlatform(): Platform

const val WINDOWS = "Windows"

const val MACOS = "Macos"

const val LINUX = "Linux"

const val IPHONE = "iPhone"

const val IPAD = "iPad"

const val ANDROID = "Android"

const val UNKNOWN_OS = "Unknown"

@Serializable
data class Platform(
    val name: String,
    val arch: String,
    val bitMode: Int,
    val version: String,
) {

    fun isWindows(): Boolean {
        return name == WINDOWS
    }

    fun isMacos(): Boolean {
        return name == MACOS
    }

    fun isLinux(): Boolean {
        return name == LINUX
    }

    fun isDesktop(): Boolean {
        return isWindows() || isMacos() || isLinux()
    }

    fun isIphone(): Boolean {
        return name == IPHONE
    }

    fun isIpad(): Boolean {
        return name == IPAD
    }

    fun isAndroid(): Boolean {
        return name == ANDROID
    }

    fun is64bit(): Boolean {
        return bitMode == 64
    }
}
