package com.crosspaste.paste

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordManagerHintsTest {

    private class FakeProbe(
        private val available: Set<String>,
        private val dwords: Map<String, Int?> = emptyMap(),
    ) : WindowsClipboardFormatProbe {
        var dwordReads = 0

        override fun isFormatAvailable(name: String): Boolean = name in available

        override fun readDword(name: String): Int? {
            dwordReads++
            return dwords[name]
        }
    }

    @Test
    fun `windows monitoring exclusion format alone conceals without opening the clipboard`() {
        val probe = FakeProbe(available = setOf(PasswordManagerHints.WINDOWS_EXCLUDE_FROM_MONITORING))
        assertTrue(PasswordManagerHints.isConcealedOnWindows(probe))
        assertTrue(probe.dwordReads == 0)
    }

    @Test
    fun `windows history flag zero conceals`() {
        val probe =
            FakeProbe(
                available = setOf(PasswordManagerHints.WINDOWS_CAN_INCLUDE_IN_HISTORY),
                dwords = mapOf(PasswordManagerHints.WINDOWS_CAN_INCLUDE_IN_HISTORY to 0),
            )
        assertTrue(PasswordManagerHints.isConcealedOnWindows(probe))
    }

    @Test
    fun `windows history flag one does not conceal`() {
        val probe =
            FakeProbe(
                available = setOf(PasswordManagerHints.WINDOWS_CAN_INCLUDE_IN_HISTORY),
                dwords = mapOf(PasswordManagerHints.WINDOWS_CAN_INCLUDE_IN_HISTORY to 1),
            )
        assertFalse(PasswordManagerHints.isConcealedOnWindows(probe))
    }

    @Test
    fun `windows history flag present but unreadable conceals (fail safe)`() {
        val probe = FakeProbe(available = setOf(PasswordManagerHints.WINDOWS_CAN_INCLUDE_IN_HISTORY))
        assertTrue(PasswordManagerHints.isConcealedOnWindows(probe))
    }

    @Test
    fun `windows plain clipboard does not conceal and never opens the clipboard`() {
        val probe = FakeProbe(available = setOf("CF_UNICODETEXT"))
        assertFalse(PasswordManagerHints.isConcealedOnWindows(probe))
        assertTrue(probe.dwordReads == 0)
    }

    @Test
    fun `linux kde hint target conceals`() {
        val targets = listOf("TARGETS", "UTF8_STRING", "text/plain", "x-kde-passwordManagerHint")
        assertTrue(PasswordManagerHints.isConcealedOnLinux(targets))
    }

    @Test
    fun `linux plain targets do not conceal`() {
        assertFalse(PasswordManagerHints.isConcealedOnLinux(listOf("TARGETS", "UTF8_STRING", "text/html")))
        assertFalse(PasswordManagerHints.isConcealedOnLinux(emptyList()))
    }
}
