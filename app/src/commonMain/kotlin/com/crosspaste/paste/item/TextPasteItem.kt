package com.crosspaste.paste.item

import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.item.PasteItem.Companion.getExtraInfoFromJson
import com.crosspaste.utils.getCodecsUtils
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
) : PasteItem, PasteText {

    companion object {
        private val codecsUtils = getCodecsUtils()

        fun createTextPasteItem(
            identifiers: List<String> = listOf(),
            text: String,
            extraInfo: JsonObject? = null,
        ): TextPasteItem {
            val textBytes = text.encodeToByteArray()
            val hash = codecsUtils.hash(textBytes)
            val size = textBytes.size.toLong()
            return TextPasteItem(
                identifiers = identifiers,
                hash = hash,
                size = size,
                text = text,
                extraInfo = extraInfo,
            )
        }
    }

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        text = jsonObject["text"]!!.jsonPrimitive.content,
        extraInfo = getExtraInfoFromJson(jsonObject),
    )

    override fun getPasteType(): PasteType {
        return PasteType.TEXT_TYPE
    }

    override fun getSearchContent(): String {
        return text.lowercase()
    }

    override fun getTitle(): String {
        return text
    }

    override fun update(
        data: Any,
        hash: String,
    ): PasteItem {
        return (data as? String)?.let { text ->
            TextPasteItem(
                identifiers = identifiers,
                hash = hash,
                size = text.length.toLong(),
                text = text,
            )
        } ?: this
    }

    override fun isValid(): Boolean {
        return hash.isNotEmpty() &&
            size > 0 &&
            text.isNotEmpty()
    }

    override fun toJson(): String {
        return buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("hash", hash)
            put("size", size)
            put("text", text)
            extraInfo?.let { put("extraInfo", it) }
        }.toString()
    }
}
