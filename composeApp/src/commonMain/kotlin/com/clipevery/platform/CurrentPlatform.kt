package com.clipevery.platform

expect fun currentPlatform(): Platform

interface Platform {
    val name: String
    val bitMode: Int
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

    fun is64bit(): Boolean {
        return bitMode == 64
    }
}
