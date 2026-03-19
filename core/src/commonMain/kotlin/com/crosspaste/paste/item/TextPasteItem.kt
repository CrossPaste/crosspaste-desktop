package com.crosspaste.paste.item

import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteItem.Companion.getExtraInfoFromJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

@Serializable
@SerialName("text")
class TextPasteItem(
    override val identifiers: List<String>,
    override val hash: String,
    override val size: Long,
    override val text: String,
    override val extraInfo: JsonObject? = null,
) : PasteItem,
    PasteText {

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        text = jsonObject["text"]!!.jsonPrimitive.content,
        extraInfo = getExtraInfoFromJson(jsonObject),
    )

    override fun getPasteType(): PasteType = PasteType.TEXT_TYPE

    override fun getSearchContent(): String = text.lowercase()

    override fun getSummary(): String = text

    override fun copy(extraInfo: JsonObject?): TextPasteItem =
        createTextPasteItem(
            identifiers = identifiers,
            text = text,
            extraInfo = extraInfo,
        )

    override fun isValid(): Boolean =
        hash.isNotEmpty() &&
            size > 0 &&
            text.isNotEmpty()

    override fun toJson(): String =
        buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("hash", hash)
            put("size", size)
            put("text", text)
            extraInfo?.let { put("extraInfo", it) }
        }.toString()
}
