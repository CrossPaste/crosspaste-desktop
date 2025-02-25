package com.crosspaste.listen

import com.crosspaste.platform.getPlatform

fun getDesktopKeyboardKeys(): KeyboardKeys {
    val platform = getPlatform()
    return if (platform.isMacos()) {
        MacKeyboardKeys
    } else if (platform.isWindows()) {
        WindowsKeyboardKeys
    } else if (platform.isLinux()) {
        LinuxKeyboardKeys
    } else {
        throw IllegalStateException("Unsupported platform: $platform")
    }
}
