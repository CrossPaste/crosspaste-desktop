package com.crosspaste.paste

import kotlin.test.Test
import kotlin.test.assertEquals

class PasteTypeTest {

    @Test
    fun `fromType resolves every persisted type id`() {
        // These ids are persisted in the database and sent over the wire, so the mapping
        // itself is the contract: a wrong entry makes old pastes decode as the wrong type.
        val expectedByRawId =
            mapOf(
                0 to PasteType.TEXT_TYPE,
                1 to PasteType.URL_TYPE,
                2 to PasteType.HTML_TYPE,
                3 to PasteType.FILE_TYPE,
                4 to PasteType.IMAGE_TYPE,
                5 to PasteType.RTF_TYPE,
                6 to PasteType.COLOR_TYPE,
            )

        expectedByRawId.forEach { (rawId, expected) ->
            assertEquals(expected, PasteType.fromType(rawId), "raw type id $rawId")
        }
    }

    @Test
    fun `fromType falls back to INVALID_TYPE for unknown ids`() {
        assertEquals(PasteType.INVALID_TYPE, PasteType.fromType(999))
        assertEquals(PasteType.INVALID_TYPE, PasteType.fromType(-2))
    }

    @Test
    fun `TYPES and MAP_TYPES describe the same set of types`() {
        // A type added to one list but not the other would silently disappear from
        // fromType() or from the UI type list.
        assertEquals(PasteType.TYPES.size, PasteType.MAP_TYPES.size)
        PasteType.TYPES.forEach { type ->
            assertEquals(type, PasteType.MAP_TYPES[type.type], "raw type id ${type.type}")
        }
        assertEquals(
            PasteType.TYPES.map { it.type }.sorted(),
            PasteType.MAP_TYPES.keys.sorted(),
        )
    }

    @Test
    fun `type priorities are unique so paste-type ordering is deterministic`() {
        val priorities = PasteType.TYPES.map { it.priority }
        assertEquals(priorities.size, priorities.distinct().size)
    }
}
