package com.crosspaste.platform

import kotlinx.serialization.Serializable

@Serializable
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

        const val HARMONY = "HarmonyOS"

        const val CHROME_EXTENSION = "ChromeExtension"

        const val UNKNOWN_OS = "Unknown"

        private const val MACOS_DISPLAY_NAME = "macOS"

        // Xcode 27.2's iPhone Duo device profile lists this modelIdentifier.
        // iOS reports that identifier in arch on device. Other iPhone19 models include iPhone 18 Pro.
        private const val IPHONE_DUO_MODEL = "iPhone19,4"
    }

    /**
     * Human-readable platform name for the UI. [name] is a wire value serialized into
     * SyncInfo/EndpointInfo and compared across versions, so it stays as is; only the
     * presentation differs (e.g. "Macos" -> "macOS").
     */
    fun displayName(): String = if (isMacos()) MACOS_DISPLAY_NAME else name

    fun isWindows(): Boolean = name == WINDOWS

    fun isMacos(): Boolean = name == MACOS

    fun isLinux(): Boolean = name == LINUX

    fun isDesktop(): Boolean = isWindows() || isMacos() || isLinux()

    fun isIphone(): Boolean = name == IPHONE

    fun isIphoneDuo(): Boolean = isIphone() && arch == IPHONE_DUO_MODEL

    fun isIpad(): Boolean = name == IPAD

    fun isApple(): Boolean = isMacos() || isIphone() || isIpad()

    fun isAndroid(): Boolean = name == ANDROID

    fun isHarmony(): Boolean = name == HARMONY

    fun isChromeExtension(): Boolean = name == CHROME_EXTENSION

    fun isExtension(): Boolean = isChromeExtension()

    fun is64bit(): Boolean = bitMode == 64
}
