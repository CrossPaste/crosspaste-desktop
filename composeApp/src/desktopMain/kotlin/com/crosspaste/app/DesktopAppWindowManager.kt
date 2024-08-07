package com.crosspaste.app

import com.crosspaste.listen.ActiveGraphicsDevice
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.currentPlatform

fun getDesktopAppWindowManager(
    lazyShortcutKeys: Lazy<ShortcutKeys>,
    activeGraphicsDevice: ActiveGraphicsDevice,
    userDataPathProvider: UserDataPathProvider,
): AppWindowManager {
    val platform = currentPlatform()
    return if (platform.isMacos()) {
        MacAppWindowManager(lazyShortcutKeys, activeGraphicsDevice, userDataPathProvider)
    } else if (platform.isWindows()) {
        WinAppWindowManager(lazyShortcutKeys, activeGraphicsDevice, userDataPathProvider)
    } else if (platform.isLinux()) {
        LinuxAppWindowManager(lazyShortcutKeys, activeGraphicsDevice, userDataPathProvider)
    } else {
        throw IllegalStateException("Unsupported platform: $platform")
    }
}
