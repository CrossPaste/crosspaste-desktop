package com.clipevery.listen

import com.clipevery.platform.currentPlatform

fun getDesktopKeyboardKeys(): KeyboardKeys {
    val platform = currentPlatform()
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
