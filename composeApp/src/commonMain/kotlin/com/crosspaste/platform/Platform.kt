package com.crosspaste.platform

import kotlinx.serialization.Serializable

expect fun currentPlatform(): Platform

@Serializable
data class Platform(
    val name: String,
    val arch: String,
    val bitMode: Int,
    val version: String,
) {

    fun isWindows(): Boolean {
        return name == "Windows"
    }

    fun isMacos(): Boolean {
        return name == "Macos"
    }

    fun isLinux(): Boolean {
        return name == "Linux"
    }

    fun isIphone(): Boolean {
        return name == "iPhone"
    }

    fun isIpad(): Boolean {
        return name == "iPad"
    }

    fun isAndroid(): Boolean {
        return name == "Android"
    }

    fun is64bit(): Boolean {
        return bitMode == 64
    }
}
