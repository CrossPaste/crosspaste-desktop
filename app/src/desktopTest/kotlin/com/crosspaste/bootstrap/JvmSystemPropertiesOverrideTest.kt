package com.crosspaste.bootstrap

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmSystemPropertiesOverrideTest {

    @Test
    fun `resolveOverrideFile picks crosspaste dir on Windows`() {
        val home = "C:\\Users\\alice"
        val file =
            JvmSystemPropertiesOverride.resolveOverrideFile(
                osName = "Windows 11",
                userHome = home,
            )!!
        assertEquals(
            File(File(home, ".crosspaste"), "jvm-system-properties.properties").path,
            file.path,
        )
    }

    @Test
    fun `resolveOverrideFile picks crosspaste dir on Linux`() {
        val file =
            JvmSystemPropertiesOverride.resolveOverrideFile(
                osName = "Linux",
                userHome = "/home/alice",
            )!!
        assertEquals(
            "/home/alice/.local/share/.crosspaste/jvm-system-properties.properties",
            file.path,
        )
    }

    @Test
    fun `resolveOverrideFile picks Application Support dir on macOS`() {
        val file =
            JvmSystemPropertiesOverride.resolveOverrideFile(
                osName = "Mac OS X",
                userHome = "/Users/alice",
            )!!
        assertEquals(
            "/Users/alice/Library/Application Support/CrossPaste/jvm-system-properties.properties",
            file.path,
        )
    }

    @Test
    fun `resolveOverrideFile returns null when user home is empty`() {
        assertNull(
            JvmSystemPropertiesOverride.resolveOverrideFile(
                osName = "Linux",
                userHome = "",
            ),
        )
    }

    @Test
    fun `isAllowed accepts whitelisted prefixes`() {
        assertTrue(JvmSystemPropertiesOverride.isAllowed("skiko.renderApi"))
        assertTrue(JvmSystemPropertiesOverride.isAllowed("compose.interop.blending"))
        assertTrue(JvmSystemPropertiesOverride.isAllowed("sun.java2d.uiScale"))
        assertTrue(JvmSystemPropertiesOverride.isAllowed("sun.awt.noerasebackground"))
        assertTrue(JvmSystemPropertiesOverride.isAllowed("awt.useSystemAAFontSettings"))
        assertTrue(JvmSystemPropertiesOverride.isAllowed("swing.aatext"))
        assertTrue(JvmSystemPropertiesOverride.isAllowed("crosspaste.debug"))
    }

    @Test
    fun `isAllowed rejects sensitive prefixes`() {
        assertFalse(JvmSystemPropertiesOverride.isAllowed("javax.net.ssl.trustStore"))
        assertFalse(JvmSystemPropertiesOverride.isAllowed("java.security.policy"))
        assertFalse(JvmSystemPropertiesOverride.isAllowed("user.home"))
        assertFalse(JvmSystemPropertiesOverride.isAllowed(""))
    }

    @Test
    fun `apply is a no-op when no file exists`() {
        val tmp = Files.createTempDirectory("jvmOverrideNoFile")
        val testKey = "skiko.renderApi"
        val original = System.getProperty(testKey)
        try {
            System.clearProperty(testKey)
            withUserHome(tmp) {
                JvmSystemPropertiesOverride.apply()
            }
            assertNull(System.getProperty(testKey))
        } finally {
            if (original != null) System.setProperty(testKey, original) else System.clearProperty(testKey)
            tmp.toFile().deleteRecursively()
        }
    }

    @Test
    fun `apply sets whitelisted properties and skips others`() {
        val tmp = Files.createTempDirectory("jvmOverrideApply")
        val dir =
            File(File(File(tmp.toFile(), ".local"), "share"), ".crosspaste").apply { mkdirs() }
        File(dir, "jvm-system-properties.properties").writeText(
            """
            skiko.renderApi=SOFTWARE_COMPAT
            crosspaste.debug.panel=true
            javax.net.ssl.trustStore=/tmp/evil
            """.trimIndent(),
        )

        val keys = listOf("skiko.renderApi", "crosspaste.debug.panel", "javax.net.ssl.trustStore")
        val originals = keys.associateWith { System.getProperty(it) }
        keys.forEach { System.clearProperty(it) }

        try {
            withUserHome(tmp) {
                JvmSystemPropertiesOverride.apply()
            }
            assertEquals("SOFTWARE_COMPAT", System.getProperty("skiko.renderApi"))
            assertEquals("true", System.getProperty("crosspaste.debug.panel"))
            assertNull(System.getProperty("javax.net.ssl.trustStore"))
        } finally {
            originals.forEach { (k, v) -> if (v != null) System.setProperty(k, v) else System.clearProperty(k) }
            tmp.toFile().deleteRecursively()
        }
    }

    private fun withUserHome(
        tmp: Path,
        block: () -> Unit,
    ) {
        val originalHome = System.getProperty("user.home")
        val originalOs = System.getProperty("os.name")
        try {
            System.setProperty("user.home", tmp.toFile().absolutePath)
            System.setProperty("os.name", "Linux")
            block()
        } finally {
            System.setProperty("user.home", originalHome)
            System.setProperty("os.name", originalOs)
        }
    }
}
