package com.clipevery.serializer

import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.service.TextItemService
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipState
import com.clipevery.dao.clip.ClipType
import com.clipevery.utils.DesktopJsonUtils
import com.clipevery.utils.EncryptUtils.md5ByString
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
    fun testClipData() {
        val textClipItem =
            TextClipItem().apply {
                this.identifier = TextItemService.TEXT
                this.text = "testClipData"
                this.md5 = md5ByString(this.text)
            }

        val clipData =
            ClipData().apply {
                this.clipId = 0
                this.clipAppearContent = RealmAny.Companion.create(textClipItem)
                this.clipContent =
                    ClipContent().apply {
                        this.clipAppearItems = realmListOf()
                    }
                this.clipSearchContent = textClipItem.text
                this.clipType = ClipType.TEXT
                this.md5 = textClipItem.md5
                this.clipState = ClipState.LOADED
                this.createTime = RealmInstant.now()
                this.appInstanceId = UUID.randomUUID().toString()
            }

        val json = DesktopJsonUtils.JSON.encodeToString(clipData)
        println(json)
        val newClipData: ClipData = DesktopJsonUtils.JSON.decodeFromString(json)
        assertEquals(clipData.clipId, newClipData.clipId)
        val newTextClipItem = ClipContent.getClipItem(newClipData.clipAppearContent)
        assertTrue(newTextClipItem is TextClipItem)
        assertEquals(textClipItem.text, newTextClipItem.text)
        assertEquals(textClipItem.md5, newTextClipItem.md5)
        assertEquals(textClipItem.identifier, newTextClipItem.identifier)
        assertEquals(clipData.clipSearchContent, newClipData.clipSearchContent)
        assertEquals(clipData.clipType, newClipData.clipType)
        assertEquals(clipData.md5, newClipData.md5)
        assertEquals(ClipState.LOADING, newClipData.clipState)
        assertNotEquals(clipData.createTime, newClipData.createTime)
        assertEquals(clipData.appInstanceId, newClipData.appInstanceId)
        assertTrue(newClipData.isRemote)
    }
}
