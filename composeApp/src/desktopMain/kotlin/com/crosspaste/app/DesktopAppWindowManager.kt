package com.crosspaste.app

import com.crosspaste.listen.ActiveGraphicsDevice
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.platform.currentPlatform

fun getDesktopAppWindowManager(
    lazyShortcutKeys: Lazy<ShortcutKeys>,
    activeGraphicsDevice: ActiveGraphicsDevice,
): AppWindowManager {
    val platform = currentPlatform()
    return if (platform.isMacos()) {
        MacAppWindowManager(lazyShortcutKeys, activeGraphicsDevice)
    } else if (platform.isWindows()) {
        WinAppWindowManager(lazyShortcutKeys, activeGraphicsDevice)
    } else if (platform.isLinux()) {
        LinuxAppWindowManager(lazyShortcutKeys, activeGraphicsDevice)
    } else {
        throw IllegalStateException("Unsupported platform: $platform")
    }
}
