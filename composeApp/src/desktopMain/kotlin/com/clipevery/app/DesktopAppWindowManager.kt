package com.clipevery.app

import com.clipevery.listen.ActiveGraphicsDevice
import com.clipevery.listener.ShortcutKeys
import com.clipevery.platform.currentPlatform

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
