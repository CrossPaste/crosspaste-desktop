package com.crosspaste.serializer

import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createRtfPasteItem
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PasteItemSerializerCompatibilityTest {

    private val json = getJsonUtils().JSON
    private val extraInfo = JsonObject(mapOf("source" to JsonPrimitive("compatibility")))
    private val htmlSerializer = HtmlPasteItemSerializer()
    private val rtfSerializer = RtfPasteItemSerializer()

    @Test
    fun `HTML extraInfo keeps the legacy string wire format`() {
        val encoded =
            json.encodeToString(
                htmlSerializer,
                createHtmlPasteItem(html = "<p>test</p>", extraInfo = extraInfo),
            )
        val encodedExtraInfo = json.parseToJsonElement(encoded).jsonObject.getValue("extraInfo")

        assertIs<JsonPrimitive>(encodedExtraInfo)
        assertEquals(extraInfo.toString(), encodedExtraInfo.content)
        assertEquals(extraInfo, json.decodeFromString(htmlSerializer, encoded).extraInfo)
    }

    @Test
    fun `RTF extraInfo keeps the legacy string wire format`() {
        val encoded =
            json.encodeToString(
                rtfSerializer,
                createRtfPasteItem(rtf = "{\\rtf1 test}", extraInfo = extraInfo),
            )
        val encodedExtraInfo = json.parseToJsonElement(encoded).jsonObject.getValue("extraInfo")

        assertIs<JsonPrimitive>(encodedExtraInfo)
        assertEquals(extraInfo.toString(), encodedExtraInfo.content)
        assertEquals(extraInfo, json.decodeFromString(rtfSerializer, encoded).extraInfo)
    }

    @Test
    fun `HTML and RTF encode absent extraInfo as JSON null`() {
        val html =
            json
                .parseToJsonElement(
                    json.encodeToString(htmlSerializer, createHtmlPasteItem(html = "test")),
                ).jsonObject
        val rtf =
            json
                .parseToJsonElement(
                    json.encodeToString(rtfSerializer, createRtfPasteItem(rtf = "test")),
                ).jsonObject

        assertEquals(JsonNull, html["extraInfo"])
        assertEquals(JsonNull, rtf["extraInfo"])
    }

    @Test
    fun `HTML and RTF accept object extraInfo from newer peers`() {
        val html = json.encodeToString(htmlSerializer, createHtmlPasteItem(html = "test", extraInfo = extraInfo))
        val rtf = json.encodeToString(rtfSerializer, createRtfPasteItem(rtf = "test", extraInfo = extraInfo))
        val htmlWithObject = replaceExtraInfoWithObject(html)
        val rtfWithObject = replaceExtraInfoWithObject(rtf)

        assertEquals(extraInfo, json.decodeFromString(htmlSerializer, htmlWithObject).extraInfo)
        assertEquals(extraInfo, json.decodeFromString(rtfSerializer, rtfWithObject).extraInfo)
    }

    private fun replaceExtraInfoWithObject(encoded: String): String {
        val original = json.parseToJsonElement(encoded).jsonObject
        return JsonObject(original + ("extraInfo" to extraInfo)).toString()
    }
}
