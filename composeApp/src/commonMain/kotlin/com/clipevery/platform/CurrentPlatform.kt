package com.clipevery.platform

expect fun currentPlatform(): Platform

interface Platform {
    val name: String
    val version: String

    fun isWindows(): Boolean {
        return name == "Windows"
    }

    fun isMacos(): Boolean {
        return name == "Macos"
    }

    fun isLinux(): Boolean {
        return name == "Linux"
    }
}
