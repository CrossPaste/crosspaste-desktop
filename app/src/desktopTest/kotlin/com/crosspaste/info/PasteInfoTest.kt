package com.crosspaste.info

import com.crosspaste.i18n.GlobalCopywriter
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasteInfoTest {

    @Test
    fun `getTextByCopyWriter delegates to converter`() {
        val info =
            PasteInfo("size", "1024") { value, _ ->
                "$value bytes"
            }
        assertEquals("1024 bytes", info.getTextByCopyWriter(mockk()))
    }

    @Test
    fun `getTextByCopyWriter converter can use copywriter for i18n`() {
        val copywriter = mockk<GlobalCopywriter>()
        every { copywriter.getText("file_size") } returns "File Size"

        val info =
            PasteInfo("size", "1024") { value, cw ->
                "${cw.getText("file_size")}: $value"
            }
        assertEquals("File Size: 1024", info.getTextByCopyWriter(copywriter))
    }

    @Test
    fun `createPasteInfoWithoutConverter returns value as-is`() {
        val info = createPasteInfoWithoutConverter("key", "hello")
        assertEquals("hello", info.getTextByCopyWriter(mockk()))
    }

    @Test
    fun `equals compares by key and value only, ignoring converter`() {
        val info1 = PasteInfo("key", "value") { v, _ -> v }
        val info2 = PasteInfo("key", "value") { v, _ -> "$v!" }
        assertTrue(info1 == info2)
    }

    @Test
    fun `equals returns false for different keys`() {
        val info1 = createPasteInfoWithoutConverter("key1", "value")
        val info2 = createPasteInfoWithoutConverter("key2", "value")
        assertFalse(info1 == info2)
    }

    @Test
    fun `equals returns false for different values`() {
        val info1 = createPasteInfoWithoutConverter("key", "value1")
        val info2 = createPasteInfoWithoutConverter("key", "value2")
        assertFalse(info1 == info2)
    }

    @Test
    fun `hashCode is consistent with equals across different converters`() {
        val info1 = PasteInfo("key", "value") { v, _ -> v }
        val info2 = PasteInfo("key", "value") { v, _ -> "$v modified" }
        assertEquals(info1.hashCode(), info2.hashCode())
    }
}
