package com.crosspaste.platform.windows

import okio.Path

/** Pure naming rules for Windows apps; kept free of JNA so they are testable on any OS. */
object WindowsAppNames {

    /** Exe file name without its `.exe` suffix, used when the version resource has no `FileDescription`. */
    fun fallbackAppName(exePath: Path): String {
        val name = exePath.name
        return if (name.endsWith(".exe", ignoreCase = true)) name.dropLast(4) else name
    }
}
