package com.crosspaste.platform.linux

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LinuxActiveAppResolverTest {

    private val tempDirs = mutableListOf<java.nio.file.Path>()

    private fun tempDir(): java.nio.file.Path = Files.createTempDirectory("crosspaste-test").also { tempDirs.add(it) }

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { it.toFile().deleteRecursively() }
    }

    // ---- Hyprland ----

    @Test
    fun `hyprland reply parses class field`() {
        val reply =
            """{"address":"0x1","class":"firefox","title":"Mozilla Firefox","pid":42,"xwayland":false}"""
        assertEquals("firefox", HyprlandActiveAppResolver.parseActiveWindowClass(reply))
    }

    @Test
    fun `hyprland empty object means no focused window`() {
        assertNull(HyprlandActiveAppResolver.parseActiveWindowClass("{}"))
    }

    @Test
    fun `hyprland non json reply maps to null`() {
        assertNull(HyprlandActiveAppResolver.parseActiveWindowClass("Invalid"))
        assertNull(HyprlandActiveAppResolver.parseActiveWindowClass(""))
    }

    @Test
    fun `hyprland blank class maps to null`() {
        assertNull(HyprlandActiveAppResolver.parseActiveWindowClass("""{"class":""}"""))
    }

    // ---- Sway ----

    @Test
    fun `sway tree resolves focused wayland app_id`() {
        val tree =
            """
            {
              "type": "root", "focused": false,
              "nodes": [
                {
                  "type": "output", "focused": false,
                  "nodes": [
                    {
                      "type": "workspace", "focused": false,
                      "nodes": [
                        {"type": "con", "focused": false, "app_id": "org.kde.dolphin"},
                        {"type": "con", "focused": true, "app_id": "foot"}
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        assertEquals("foot", SwayActiveAppResolver.parseFocusedAppName(tree))
    }

    @Test
    fun `sway tree resolves focused xwayland window class`() {
        val tree =
            """
            {
              "focused": false,
              "nodes": [
                {
                  "focused": true,
                  "app_id": null,
                  "window_properties": {"class": "jetbrains-idea", "instance": "jetbrains-idea"}
                }
              ]
            }
            """.trimIndent()
        assertEquals("jetbrains-idea", SwayActiveAppResolver.parseFocusedAppName(tree))
    }

    @Test
    fun `sway tree searches floating nodes`() {
        val tree =
            """
            {
              "focused": false,
              "nodes": [{"focused": false, "app_id": "background"}],
              "floating_nodes": [{"focused": true, "app_id": "pavucontrol"}]
            }
            """.trimIndent()
        assertEquals("pavucontrol", SwayActiveAppResolver.parseFocusedAppName(tree))
    }

    @Test
    fun `sway focused workspace without app maps to null`() {
        val tree =
            """
            {
              "focused": false,
              "nodes": [{"type": "workspace", "focused": true, "nodes": []}]
            }
            """.trimIndent()
        assertNull(SwayActiveAppResolver.parseFocusedAppName(tree))
    }

    @Test
    fun `sway garbage maps to null`() {
        assertNull(SwayActiveAppResolver.parseFocusedAppName("not json"))
        assertNull(SwayActiveAppResolver.parseFocusedAppName("[]"))
    }

    // ---- backend detection ----

    @Test
    fun `x11 session picks unguarded x11 resolver`() {
        val resolver = LinuxActiveAppResolver.detect { name -> if (name == "XDG_SESSION_TYPE") "x11" else null }
        assertIs<X11ActiveAppResolver>(resolver)
    }

    @Test
    fun `wayland session without compositor ipc falls back to guarded x11`() {
        val resolver = LinuxActiveAppResolver.detect { name -> if (name == "XDG_SESSION_TYPE") "wayland" else null }
        assertIs<X11ActiveAppResolver>(resolver)
    }

    @Test
    fun `hyprland socket is detected`() {
        val runtimeDir = tempDir()
        val socket = runtimeDir.resolve("hypr/sig123/.socket.sock")
        socket.parent.createDirectories()
        Files.createFile(socket)
        val env =
            mapOf(
                "XDG_SESSION_TYPE" to "wayland",
                "HYPRLAND_INSTANCE_SIGNATURE" to "sig123",
                "XDG_RUNTIME_DIR" to runtimeDir.toString(),
            )
        val resolver = LinuxActiveAppResolver.detect { env[it] }
        assertIs<HyprlandActiveAppResolver>(resolver)
    }

    @Test
    fun `sway socket is detected`() {
        val socket = tempDir().resolve("sway-ipc.sock")
        Files.createFile(socket)
        val env =
            mapOf(
                "XDG_SESSION_TYPE" to "wayland",
                "SWAYSOCK" to socket.toString(),
            )
        val resolver = LinuxActiveAppResolver.detect { env[it] }
        assertIs<SwayActiveAppResolver>(resolver)
    }

    // ---- desktop entry icon ----

    @Test
    fun `desktop entry icon name is parsed from desktop entry section only`() {
        val content =
            """
            [Desktop Entry]
            Name=Firefox
            Icon=firefox-logo
            Exec=firefox %u

            [Desktop Action new-window]
            Icon=other-icon
            """.trimIndent()
        assertEquals("firefox-logo", LinuxDesktopAppIcon.parseDesktopIconName(content))
    }

    @Test
    fun `desktop entry without icon maps to null`() {
        assertNull(LinuxDesktopAppIcon.parseDesktopIconName("[Desktop Entry]\nName=Foo"))
    }

    @Test
    fun `icon png is found via desktop entry and hicolor theme`() {
        val dataDir = tempDir()
        dataDir
            .resolve("applications")
            .createDirectories()
            .resolve("org.mozilla.firefox.desktop")
            .writeText("[Desktop Entry]\nIcon=firefox-logo\n")
        val iconFile =
            dataDir
                .resolve("icons/hicolor/128x128/apps")
                .createDirectories()
                .resolve("firefox-logo.png")
        Files.write(iconFile, byteArrayOf(1, 2, 3))

        assertEquals(iconFile, LinuxDesktopAppIcon.findIconPng("org.mozilla.firefox", listOf(dataDir)))
    }

    @Test
    fun `app id doubles as icon name when no desktop file exists`() {
        val dataDir = tempDir()
        val iconFile =
            dataDir
                .resolve("pixmaps")
                .createDirectories()
                .resolve("foot.png")
        Files.write(iconFile, byteArrayOf(1))

        assertEquals(iconFile, LinuxDesktopAppIcon.findIconPng("foot", listOf(dataDir)))
    }

    @Test
    fun `larger icon sizes win over pixmaps`() {
        val dataDir = tempDir()
        val big =
            dataDir
                .resolve("icons/hicolor/256x256/apps")
                .createDirectories()
                .resolve("foot.png")
        Files.write(big, byteArrayOf(1))
        val pixmap =
            dataDir
                .resolve("pixmaps")
                .createDirectories()
                .resolve("foot.png")
        Files.write(pixmap, byteArrayOf(2))

        assertEquals(big, LinuxDesktopAppIcon.findIconPng("foot", listOf(dataDir)))
    }

    @Test
    fun `missing icon maps to null`() {
        assertNull(LinuxDesktopAppIcon.findIconPng("ghost-app", listOf(tempDir())))
    }
}
