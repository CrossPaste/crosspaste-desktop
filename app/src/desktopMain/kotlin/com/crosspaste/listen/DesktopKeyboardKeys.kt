package com.crosspaste.listen

import com.crosspaste.platform.Platform

fun getDesktopKeyboardKeys(platform: Platform): KeyboardKeys =
    if (platform.isMacos()) {
        MacKeyboardKeys
    } else if (platform.isWindows()) {
        WindowsKeyboardKeys
    } else if (platform.isLinux()) {
        LinuxKeyboardKeys
    } else {
        throw IllegalStateException("Unsupported platform: $platform")
    }
