package com.crosspaste.paste

import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PasteStoredCodecTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    @Test
    fun `stored collection roundtrip keeps every item`() {
        val original =
            PasteCollection(
                listOf(
                    createTextPasteItem(text = "first"),
                    createTextPasteItem(text = "second"),
                ),
            )

        val restored = PasteCollection.fromStoredJson(original.toStoredJson())

        assertEquals(original.pasteItems.size, restored.pasteItems.size)
        assertEquals(original.pasteItems.map { it.toStoredJson() }, restored.pasteItems.map { it.toStoredJson() })
        assertEquals(emptyList(), restored.pasteItems.first().identifiers)
    }

    @Test
    fun `stored collection rejects unknown item type atomically`() {
        val stored =
            buildJsonArray {
                add(createTextPasteItem(text = "known").toStoredJson())
                add("""{"type":999,"hash":"future"}""")
            }.toString()

        assertFailsWith<IllegalArgumentException> {
            PasteCollection.fromStoredJson(stored)
        }
    }

    @Test
    fun `stored PasteData rejects unknown appear item`() {
        val stored =
            buildJsonObject {
                put("appInstanceId", "compatibility-test")
                put("favorite", false)
                put("pasteAppearItem", """{"type":999,"hash":"future"}""")
                put("pasteCollection", "[]")
                put("pasteType", 999)
                put("size", 0L)
                put("hash", "future")
            }.toString()

        assertNull(PasteData.fromStoredJson(stored))
    }

    @Test
    fun `database mapper tolerates unknown appear item and skips bad collection items`() {
        val knownItem = createTextPasteItem(text = "known")
        val storedCollection =
            buildJsonArray {
                add(knownItem.toStoredJson())
                add("""{"type":999,"hash":"future"}""")
                add("not-json")
            }.toString()

        val mapped = mapDatabaseRow("""{"type":999,"hash":"future"}""", storedCollection)

        assertNull(mapped.pasteAppearItem)
        assertEquals(1, mapped.pasteCollection.pasteItems.size)
        assertEquals(
            knownItem.toStoredJson(),
            mapped.pasteCollection.pasteItems
                .single()
                .toStoredJson(),
        )
    }

    @Test
    fun `database mapper uses empty collection when stored JSON is corrupt`() {
        val mapped = mapDatabaseRow(null, "not-json")

        assertEquals(emptyList(), mapped.pasteCollection.pasteItems)
    }

    private fun mapDatabaseRow(
        pasteAppearItem: String?,
        pasteCollection: String,
    ): PasteData =
        PasteData.mapper(
            id = 1L,
            appInstanceId = "database-test",
            favorite = false,
            pasteAppearItem = pasteAppearItem,
            pasteCollection = pasteCollection,
            pasteType = PasteType.TEXT_TYPE.type.toLong(),
            source = null,
            size = 0L,
            hash = "database-test",
            createTime = 1L,
            pasteSearchContent = null,
            pasteState = PasteState.LOADED.toLong(),
            remote = false,
        )
}
