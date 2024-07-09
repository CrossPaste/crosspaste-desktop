package com.crosspaste.serializer

import com.crosspaste.dao.paste.PasteCollection
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteState
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.service.TextItemService
import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.getCodecsUtils
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import kotlinx.serialization.encodeToString
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SerializerTest {

    @Test
    fun testPasteData() {
        val codecsUtils = getCodecsUtils()
        val textPasteItem =
            TextPasteItem().apply {
                this.identifier = TextItemService.TEXT
                this.text = "testPasteData"
                this.md5 = codecsUtils.md5ByString(this.text)
            }

        val pasteData =
            PasteData().apply {
                this.pasteId = 0
                this.pasteAppearItem = RealmAny.Companion.create(textPasteItem)
                this.pasteCollection =
                    PasteCollection().apply {
                        this.pasteItems = realmListOf()
                    }
                this.pasteSearchContent = textPasteItem.text.lowercase()
                this.pasteType = PasteType.TEXT
                this.md5 = textPasteItem.md5
                this.pasteState = PasteState.LOADED
                this.createTime = RealmInstant.now()
                this.appInstanceId = UUID.randomUUID().toString()
            }

        val json = DesktopJsonUtils.JSON.encodeToString(pasteData)
        println(json)
        val newPasteData: PasteData = DesktopJsonUtils.JSON.decodeFromString(json)
        assertEquals(pasteData.pasteId, newPasteData.pasteId)
        val newTextPasteItem = PasteCollection.getPasteItem(newPasteData.pasteAppearItem)
        assertTrue(newTextPasteItem is TextPasteItem)
        assertEquals(textPasteItem.text, newTextPasteItem.text)
        assertEquals(textPasteItem.md5, newTextPasteItem.md5)
        assertEquals(textPasteItem.identifier, newTextPasteItem.identifier)
        assertEquals(pasteData.pasteSearchContent, newPasteData.pasteSearchContent)
        assertEquals(pasteData.pasteType, newPasteData.pasteType)
        assertEquals(pasteData.md5, newPasteData.md5)
        assertEquals(PasteState.LOADING, newPasteData.pasteState)
        assertNotEquals(pasteData.createTime, newPasteData.createTime)
        assertEquals(pasteData.appInstanceId, newPasteData.appInstanceId)
        assertTrue(newPasteData.remote)
    }
}
