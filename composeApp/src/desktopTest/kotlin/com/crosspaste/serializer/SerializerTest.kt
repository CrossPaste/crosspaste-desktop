package com.crosspaste.serializer

import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.plugin.type.TextTypePlugin
import com.crosspaste.realm.paste.PasteCollection
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteState
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getJsonUtils
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

    private val jsonUtils = getJsonUtils()

    @Test
    fun testPasteData() {
        val codecsUtils = getCodecsUtils()
        val textPasteItem =
            TextPasteItem().apply {
                this.identifier = TextTypePlugin.TEXT
                this.text = "testPasteData"
                this.hash = codecsUtils.hashByString(this.text)
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
                this.hash = textPasteItem.hash
                this.pasteState = PasteState.LOADED
                this.createTime = RealmInstant.now()
                this.appInstanceId = UUID.randomUUID().toString()
            }

        val json = jsonUtils.JSON.encodeToString(pasteData)
        println(json)
        val newPasteData: PasteData = jsonUtils.JSON.decodeFromString(json)
        assertEquals(pasteData.pasteId, newPasteData.pasteId)
        val newTextPasteItem = PasteCollection.getPasteItem(newPasteData.pasteAppearItem)
        assertTrue(newTextPasteItem is TextPasteItem)
        assertEquals(textPasteItem.text, newTextPasteItem.text)
        assertEquals(textPasteItem.hash, newTextPasteItem.hash)
        assertEquals(textPasteItem.identifier, newTextPasteItem.identifier)
        assertEquals(pasteData.pasteSearchContent, newPasteData.pasteSearchContent)
        assertEquals(pasteData.pasteType, newPasteData.pasteType)
        assertEquals(pasteData.hash, newPasteData.hash)
        assertEquals(PasteState.LOADING, newPasteData.pasteState)
        assertNotEquals(pasteData.createTime, newPasteData.createTime)
        assertEquals(pasteData.appInstanceId, newPasteData.appInstanceId)
        assertTrue(newPasteData.remote)
    }
}
