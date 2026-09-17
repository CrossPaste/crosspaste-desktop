package com.crosspaste.platform.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxDesktopEntryTest {

    private val bitwarden =
        """
        [Desktop Entry]
        Name=Bitwarden
        Exec=/opt/Bitwarden/bitwarden %U
        Icon=bitwarden
        StartupWMClass=Bitwarden
        Type=Application

        [Desktop Action new-window]
        Name=New Window
        Icon=other-icon
        """.trimIndent()

    @Test
    fun `source name prefers StartupWMClass`() {
        assertEquals("Bitwarden", LinuxDesktopEntry.sourceName(bitwarden, "bitwarden.desktop"))
    }

    @Test
    fun `source name falls back to the entry file name`() {
        val content = "[Desktop Entry]\nName=Foo\nExec=foo\n"
        assertEquals("org.example.Foo", LinuxDesktopEntry.sourceName(content, "org.example.Foo.desktop"))
        assertEquals("Foo", LinuxDesktopEntry.sourceName(content, "Foo.DESKTOP"))
    }

    @Test
    fun `value reads only the Desktop Entry group`() {
        assertEquals("bitwarden", LinuxDesktopEntry.value(bitwarden, "Icon"))
        assertEquals("bitwarden", LinuxDesktopAppIcon.parseDesktopIconName(bitwarden))
        assertNull(LinuxDesktopEntry.value(bitwarden, "Missing"))
        assertNull(LinuxDesktopEntry.value("[Desktop Entry]\nIcon=\n", "Icon"))
    }

    @Test
    fun `value ignores keys that merely share a prefix`() {
        val content = "[Desktop Entry]\nNameLong=Long\nName=Short\n"
        assertEquals("Short", LinuxDesktopEntry.value(content, "Name"))
    }
}
