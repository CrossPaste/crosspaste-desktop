package com.crosspaste.paste.item

import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.item.PasteItem.Companion.getExtraInfoFromJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

@Serializable
@SerialName("color")
class ColorPasteItem(
    override val identifiers: List<String>,
    override val hash: String,
    override val size: Long,
    override val color: Long,
    override val extraInfo: JsonObject? = null,
) : PasteItem,
    PasteColor {

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        color = jsonObject["color"]!!.jsonPrimitive.content.toLong(),
        extraInfo = getExtraInfoFromJson(jsonObject),
    )

    override fun getPasteType(): PasteType = PasteType.COLOR_TYPE

    override fun getSearchContent(): String = toHexString()

    override fun getSummary(): String = toHexString()

    override fun update(
        data: Any,
        hash: String,
    ): PasteItem =
        (data as? Long)?.let { color ->
            ColorPasteItem(
                identifiers = identifiers,
                hash = hash,
                size = 8,
                color = color,
            )
        } ?: this

    override fun isValid(): Boolean = size == 8L && hash.isNotEmpty() && hash == color.toString()

    override fun toJson(): String =
        buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("hash", hash)
            put("size", size)
            put("color", color)
            extraInfo?.let { put("extraInfo", it) }
        }.toString()
}
