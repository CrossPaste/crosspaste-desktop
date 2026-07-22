package com.crosspaste.paste

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasteTypeTest {

    // --- fromType ---

    @Test
    fun `fromType returns TEXT_TYPE for type 0`() {
        assertEquals(PasteType.TEXT_TYPE, PasteType.fromType(0))
    }

    @Test
    fun `fromType returns URL_TYPE for type 1`() {
        assertEquals(PasteType.URL_TYPE, PasteType.fromType(1))
    }

    @Test
    fun `fromType returns HTML_TYPE for type 2`() {
        assertEquals(PasteType.HTML_TYPE, PasteType.fromType(2))
    }

    @Test
    fun `fromType returns FILE_TYPE for type 3`() {
        assertEquals(PasteType.FILE_TYPE, PasteType.fromType(3))
    }

    @Test
    fun `fromType returns IMAGE_TYPE for type 4`() {
        assertEquals(PasteType.IMAGE_TYPE, PasteType.fromType(4))
    }

    @Test
    fun `fromType returns RTF_TYPE for type 5`() {
        assertEquals(PasteType.RTF_TYPE, PasteType.fromType(5))
    }

    @Test
    fun `fromType returns COLOR_TYPE for type 6`() {
        assertEquals(PasteType.COLOR_TYPE, PasteType.fromType(6))
    }

    @Test
    fun `fromType returns INVALID_TYPE for unknown type`() {
        assertEquals(PasteType.INVALID_TYPE, PasteType.fromType(999))
        assertEquals(PasteType.INVALID_TYPE, PasteType.fromType(-2))
    }

    // --- Type check methods ---

    @Test
    fun `isText returns true only for TEXT_TYPE`() {
        assertTrue(PasteType.TEXT_TYPE.isText())
        assertFalse(PasteType.URL_TYPE.isText())
        assertFalse(PasteType.FILE_TYPE.isText())
    }

    @Test
    fun `isUrl returns true only for URL_TYPE`() {
        assertTrue(PasteType.URL_TYPE.isUrl())
        assertFalse(PasteType.TEXT_TYPE.isUrl())
    }

    @Test
    fun `isHtml returns true only for HTML_TYPE`() {
        assertTrue(PasteType.HTML_TYPE.isHtml())
        assertFalse(PasteType.TEXT_TYPE.isHtml())
    }

    @Test
    fun `isFile returns true only for FILE_TYPE`() {
        assertTrue(PasteType.FILE_TYPE.isFile())
        assertFalse(PasteType.IMAGE_TYPE.isFile())
    }

    @Test
    fun `isImage returns true only for IMAGE_TYPE`() {
        assertTrue(PasteType.IMAGE_TYPE.isImage())
        assertFalse(PasteType.FILE_TYPE.isImage())
    }

    @Test
    fun `isRtf returns true only for RTF_TYPE`() {
        assertTrue(PasteType.RTF_TYPE.isRtf())
        assertFalse(PasteType.HTML_TYPE.isRtf())
    }

    @Test
    fun `isColor returns true only for COLOR_TYPE`() {
        assertTrue(PasteType.COLOR_TYPE.isColor())
        assertFalse(PasteType.TEXT_TYPE.isColor())
    }

    @Test
    fun `isInValid returns true only for INVALID_TYPE`() {
        assertTrue(PasteType.INVALID_TYPE.isInValid())
        assertFalse(PasteType.TEXT_TYPE.isInValid())
        assertFalse(PasteType.FILE_TYPE.isInValid())
    }

    // --- TYPES list ---

    @Test
    fun `TYPES contains all valid types`() {
        assertEquals(7, PasteType.TYPES.size)
        assertTrue(PasteType.TYPES.contains(PasteType.TEXT_TYPE))
        assertTrue(PasteType.TYPES.contains(PasteType.URL_TYPE))
        assertTrue(PasteType.TYPES.contains(PasteType.HTML_TYPE))
        assertTrue(PasteType.TYPES.contains(PasteType.FILE_TYPE))
        assertTrue(PasteType.TYPES.contains(PasteType.IMAGE_TYPE))
        assertTrue(PasteType.TYPES.contains(PasteType.RTF_TYPE))
        assertTrue(PasteType.TYPES.contains(PasteType.COLOR_TYPE))
    }

    @Test
    fun `TYPES does not contain INVALID_TYPE`() {
        assertFalse(PasteType.TYPES.contains(PasteType.INVALID_TYPE))
    }

    // --- MAP_TYPES ---

    @Test
    fun `MAP_TYPES maps all valid type ids`() {
        assertEquals(7, PasteType.MAP_TYPES.size)
        for (type in PasteType.TYPES) {
            assertEquals(type, PasteType.MAP_TYPES[type.type])
        }
    }

    // --- Priority ---

    @Test
    fun `each type has unique priority`() {
        val priorities = PasteType.TYPES.map { it.priority }
        assertEquals(priorities.size, priorities.distinct().size)
    }

    // --- Name ---

    @Test
    fun `type names are non-empty`() {
        for (type in PasteType.TYPES) {
            assertTrue(type.name.isNotEmpty())
        }
    }
}
