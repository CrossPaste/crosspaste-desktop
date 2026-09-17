package com.crosspaste.platform.linux

/** Minimal reader for freedesktop `.desktop` files (the `[Desktop Entry]` group only). */
object LinuxDesktopEntry {

    /** Value of [key] in the `[Desktop Entry]` group, or null when absent or empty. */
    fun value(
        content: String,
        key: String,
    ): String? {
        var inDesktopEntry = false
        val prefix = "$key="
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith("[")) {
                inDesktopEntry = line == "[Desktop Entry]"
                continue
            }
            if (inDesktopEntry && line.startsWith(prefix)) {
                return line.removePrefix(prefix).trim().takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    /**
     * The source name clipboard changes from this app are attributed to. Linux
     * sources are the X11 `WM_CLASS` class (or the Wayland app id); a desktop
     * entry states it as `StartupWMClass`, otherwise the entry's file name is
     * the best available guess.
     */
    fun sourceName(
        content: String,
        fileName: String,
    ): String = value(content, "StartupWMClass") ?: stripDesktopSuffix(fileName)

    private fun stripDesktopSuffix(fileName: String): String =
        if (fileName.endsWith(".desktop", ignoreCase = true)) fileName.dropLast(".desktop".length) else fileName
}
