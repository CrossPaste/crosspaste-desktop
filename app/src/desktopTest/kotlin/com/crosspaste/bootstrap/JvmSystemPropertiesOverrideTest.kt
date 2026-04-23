package com.crosspaste.bootstrap

import com.crosspaste.presist.FilePersist
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmSystemPropertiesOverrideTest {

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
    fun `apply is a no-op when override file does not exist`() {
        val tmp = Files.createTempDirectory("jvmOverrideNoFile")
        val testKey = "skiko.renderApi"
        val original = System.getProperty(testKey)
        try {
            System.clearProperty(testKey)
            val persist =
                FilePersist.createOneFilePersist(
                    File(tmp.toFile(), JvmSystemPropertiesOverride.FILE_NAME).toOkioPath(),
                )
            JvmSystemPropertiesOverride.apply(persist)
            assertNull(System.getProperty(testKey))
        } finally {
            if (original != null) System.setProperty(testKey, original) else System.clearProperty(testKey)
            tmp.toFile().deleteRecursively()
        }
    }

    @Test
    fun `apply sets whitelisted properties and skips others`() {
        val tmp = Files.createTempDirectory("jvmOverrideApply")
        val file = File(tmp.toFile(), JvmSystemPropertiesOverride.FILE_NAME)
        file.writeText(
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
            val persist = FilePersist.createOneFilePersist(file.toOkioPath())
            JvmSystemPropertiesOverride.apply(persist)
            assertEquals("SOFTWARE_COMPAT", System.getProperty("skiko.renderApi"))
            assertEquals("true", System.getProperty("crosspaste.debug.panel"))
            assertNull(System.getProperty("javax.net.ssl.trustStore"))
        } finally {
            originals.forEach { (k, v) -> if (v != null) System.setProperty(k, v) else System.clearProperty(k) }
            tmp.toFile().deleteRecursively()
        }
    }
}
