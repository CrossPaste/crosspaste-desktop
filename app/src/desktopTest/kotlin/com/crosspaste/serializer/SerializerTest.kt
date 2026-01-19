package com.crosspaste.serializer

import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getJsonUtils
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SerializerTest {

    private val jsonUtils = getJsonUtils()

    @Test
    fun testPasteData() {
        val textPasteItem =
            createTextPasteItem(
                identifiers = listOf(DesktopTextTypePlugin.TEXT),
                text = "testPasteData",
            )

        val pasteData =
            PasteData(
                pasteAppearItem = textPasteItem,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.TEXT_TYPE.type,
                pasteSearchContent = textPasteItem.text.lowercase(),
                hash = textPasteItem.hash,
                size = textPasteItem.size,
                pasteState = PasteState.LOADED,
                createTime = DateUtils.nowEpochMilliseconds(),
                appInstanceId = UUID.randomUUID().toString(),
            )

        val json = jsonUtils.JSON.encodeToString(pasteData)
        println(json)
        val newPasteData: PasteData = jsonUtils.JSON.decodeFromString(json)
        val newTextPasteItem = newPasteData.pasteAppearItem
        assertTrue(newTextPasteItem is TextPasteItem)
        assertEquals(textPasteItem.text, newTextPasteItem.text)
        assertEquals(textPasteItem.hash, newTextPasteItem.hash)
        assertEquals(textPasteItem.identifiers, newTextPasteItem.identifiers)
        assertEquals(pasteData.pasteSearchContent, newPasteData.pasteSearchContent)
        assertEquals(pasteData.pasteType, newPasteData.pasteType)
        assertEquals(pasteData.hash, newPasteData.hash)
        assertEquals(PasteState.LOADING, newPasteData.pasteState)
        assertNotEquals(pasteData.createTime, newPasteData.createTime)
        assertEquals(pasteData.appInstanceId, newPasteData.appInstanceId)
        assertTrue(newPasteData.remote)
    }
}
