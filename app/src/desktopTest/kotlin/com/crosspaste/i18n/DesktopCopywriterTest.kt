package com.crosspaste.i18n

import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.EN
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.LANGUAGE_LIST
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopCopywriterTest {

    @Test
    fun `getText returns non-empty value for known key`() {
        val copywriter = DesktopCopywriter(EN)
        val text = copywriter.getText("current_language")
        assertTrue(text.isNotEmpty())
    }

    @Test
    fun `getText returns empty string for unknown key`() {
        val copywriter = DesktopCopywriter(EN)
        assertEquals("", copywriter.getText("nonexistent_key_12345"))
    }

    @Test
    fun `getKeys returns all loaded i18n keys`() {
        val copywriter = DesktopCopywriter(EN)
        val keys = copywriter.getKeys()
        assertTrue(keys.isNotEmpty())
        assertTrue(keys.contains("current_language"))
    }

    @Test
    fun `all supported languages load successfully with keys`() {
        for (lang in LANGUAGE_LIST) {
            val copywriter = DesktopCopywriter(lang)
            assertTrue(copywriter.getKeys().isNotEmpty(), "Language $lang should have keys")
        }
    }

    @Test
    fun `all supported languages have current_language key`() {
        for (lang in LANGUAGE_LIST) {
            val copywriter = DesktopCopywriter(lang)
            assertTrue(
                copywriter.getText("current_language").isNotEmpty(),
                "Language $lang should have current_language",
            )
        }
    }

    @Test
    fun `different languages produce different current_language values`() {
        val enText = DesktopCopywriter("en").getText("current_language")
        val zhText = DesktopCopywriter("zh").getText("current_language")
        assertNotEquals(enText, zhText)
    }

    @Test
    fun `unknown language falls back to English`() {
        val copywriter = DesktopCopywriter("xx_nonexistent")
        assertTrue(copywriter.getKeys().isNotEmpty(), "Should fall back to English")
    }
}
