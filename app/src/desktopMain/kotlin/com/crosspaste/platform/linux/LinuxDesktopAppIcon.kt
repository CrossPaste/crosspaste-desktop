package com.crosspaste.platform.linux

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Resolves an application icon from freedesktop desktop entries, for apps that
 * have no X11 window to capture `_NET_WM_ICON` from (i.e. native Wayland
 * windows reported by a compositor IPC as an `app_id`/class).
 *
 * Lookup: find `applications/<appId>.desktop` across the XDG data dirs, read
 * its `Icon=` key (falling back to the app id itself, which usually doubles as
 * the icon name), then search `icons/hicolor/<size>x<size>/apps/` (largest
 * first) and `pixmaps/` for a PNG. SVG-only themes are not rendered — the app
 * then simply has no icon, matching how an unresolvable X11 icon behaves.
 */
object LinuxDesktopAppIcon {

    private val logger = KotlinLogging.logger {}

    private val ICON_SIZES = listOf(512, 256, 192, 128, 96, 64, 48)

    /**
     * Copies the best icon PNG found for [appId] to [iconPath]. Returns false
     * when no PNG icon could be resolved.
     */
    fun saveAppIcon(
        appId: String,
        iconPath: Path,
        env: (String) -> String? = System::getenv,
    ): Boolean {
        val source = findIconPng(appId, dataDirs(env)) ?: return false
        return runCatching {
            Files.copy(source, iconPath, StandardCopyOption.REPLACE_EXISTING)
            true
        }.getOrElse { e ->
            logger.warn(e) { "Failed to copy icon $source for $appId" }
            false
        }
    }

    fun dataDirs(env: (String) -> String?): List<Path> {
        val dataHome =
            env("XDG_DATA_HOME")
                ?: env("HOME")?.let { "$it/.local/share" }
        val dataDirs = (env("XDG_DATA_DIRS") ?: "/usr/local/share:/usr/share").split(':')
        return (listOfNotNull(dataHome) + dataDirs)
            .filter { it.isNotBlank() }
            .map(Path::of)
    }

    fun findIconPng(
        appId: String,
        dataDirs: List<Path>,
    ): Path? {
        val iconName = desktopEntryIconName(appId, dataDirs) ?: appId
        if (iconName.startsWith('/')) {
            return Path.of(iconName).takeIf { it.toString().endsWith(".png") && Files.isRegularFile(it) }
        }
        for (size in ICON_SIZES) {
            for (dir in dataDirs) {
                val candidate = dir.resolve("icons/hicolor/${size}x$size/apps/$iconName.png")
                if (Files.isRegularFile(candidate)) {
                    return candidate
                }
            }
        }
        for (dir in dataDirs) {
            val candidate = dir.resolve("pixmaps/$iconName.png")
            if (Files.isRegularFile(candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun desktopEntryIconName(
        appId: String,
        dataDirs: List<Path>,
    ): String? {
        for (dir in dataDirs) {
            for (fileName in linkedSetOf("$appId.desktop", "${appId.lowercase()}.desktop")) {
                val desktopFile = dir.resolve("applications").resolve(fileName)
                if (Files.isRegularFile(desktopFile)) {
                    runCatching { Files.readString(desktopFile) }
                        .getOrNull()
                        ?.let { parseDesktopIconName(it) }
                        ?.let { return it }
                }
            }
        }
        return null
    }

    /** Reads the `Icon=` key of the `[Desktop Entry]` section. */
    fun parseDesktopIconName(content: String): String? {
        var inDesktopEntry = false
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith("[")) {
                inDesktopEntry = line == "[Desktop Entry]"
                continue
            }
            if (inDesktopEntry && line.startsWith("Icon=")) {
                return line.removePrefix("Icon=").trim().takeIf { it.isNotEmpty() }
            }
        }
        return null
    }
}
