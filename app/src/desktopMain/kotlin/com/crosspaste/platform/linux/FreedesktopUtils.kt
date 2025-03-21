package com.crosspaste.platform.linux

import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.getFileUtils
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

object FreedesktopUtils {

    private val themeName = getCurrentTheme()

    private val extMap: Map<String, String> =
        DesktopResourceUtils.loadProperties("file-ext/linux-ext-map.properties")
            .map { it.key.toString() to it.value.toString() }
            .toMap()

    fun saveExtIcon(
        ext: String,
        savePath: Path,
    ) {
        extMap[ext]?.let { iconName ->
            findLargeIcon(iconName)?.let { iconFile ->
                getFileUtils().copyPath(iconFile.toOkioPath(), savePath)
            }
        }
    }

    private fun getCurrentTheme(): String {
        val commands =
            listOf(
                // GNOME
                "gsettings get org.gnome.desktop.interface icon-theme",
                // KDE
                "kreadconfig5 --group Icons --key Theme",
                // Xfce
                "xfconf-query -c xsettings -p /Net/IconThemeName",
                // MATE
                "gsettings get org.mate.interface icon-theme",
            )

        for (command in commands) {
            runCatching {
                val result =
                    Runtime.getRuntime().exec(command)
                        .inputStream.bufferedReader().readText().trim().removeSurrounding("'")
                if (result.isNotEmpty()) {
                    return result
                }
            }
        }

        return "hicolor"
    }

    private fun findLargeIcon(iconName: String): File? {
        val sizes = listOf(512, 256, 128)
        val homeDir = System.getProperty("user.home")

        fun findIconInTheme(theme: String): File? {
            val themeDirs =
                listOf(
                    "/usr/share/icons/$theme",
                    "$homeDir/.icons/$theme",
                    "$homeDir/.local/share/icons/$theme",
                )

            for (dir in themeDirs) {
                for (size in sizes) {
                    val path = "$dir/${size}x$size/mimetypes/$iconName"
                    val file = File(path)
                    if (file.exists()) {
                        return file
                    }
                }
            }
            return null
        }

        findIconInTheme(themeName)?.let { return it }

        val inheritedThemes =
            File("/usr/share/icons/$themeName/index.theme")
                .readLines()
                .find { it.startsWith("Inherits=") }
                ?.substringAfter("=")
                ?.split(",")
                ?: emptyList()

        for (inheritedTheme in inheritedThemes) {
            findIconInTheme(inheritedTheme)?.let { return it }
        }

        return findIconInTheme("hicolor")
    }
}
