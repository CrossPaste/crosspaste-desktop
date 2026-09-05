package com.crosspaste.net.routing

import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PullRoutingTest {

    private val json = getJsonUtils().JSON
    private val pasteDataListSerializer = ListSerializer(PasteData.serializer())

    @Test
    fun `paste batch keeps newest prefix within encoded byte limit`() {
        val pastes = listOf(pasteData("first"), pasteData("second"), pasteData("third"))
        val firstTwoSize = encodedSize(pastes.take(2))

        val batch = pastes.encodeJsonArrayWithinLimit(firstTwoSize)

        assertEquals(2, batch.count)
        assertEquals(listOf("first", "second"), decodeSources(batch))
        assertEquals(
            firstTwoSize,
            batch.json
                .encodeToByteArray()
                .size
                .toLong(),
        )
    }

    @Test
    fun `paste batch does not skip oversized middle item`() {
        val pastes = listOf(pasteData("first"), pasteData("x".repeat(100)), pasteData("third"))
        val firstSize = encodedSize(pastes.take(1))

        val batch = pastes.encodeJsonArrayWithinLimit(firstSize)

        assertEquals(listOf("first"), decodeSources(batch))
    }

    @Test
    fun `paste batch keeps one item when it exceeds batch limit`() {
        val first = pasteData("x".repeat(100))

        val batch = listOf(first, pasteData("second")).encodeJsonArrayWithinLimit(2)

        assertEquals(listOf(first.source), decodeSources(batch))
        assertTrue(batch.json.encodeToByteArray().size > 2)
    }

    private fun encodedSize(pasteData: List<PasteData>): Long =
        json
            .encodeToString(pasteDataListSerializer, pasteData)
            .encodeToByteArray()
            .size
            .toLong()

    // createTime is not serialized, so compare by source instead of whole objects.
    private fun decodeSources(batch: EncodedPasteBatch): List<String> =
        json.decodeFromString(pasteDataListSerializer, batch.json).map { it.source.orEmpty() }

    private fun pasteData(source: String): PasteData =
        PasteData(
            appInstanceId = "local",
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.TEXT_TYPE.type,
            source = source,
            size = source.length.toLong(),
            hash = source,
        )
}
