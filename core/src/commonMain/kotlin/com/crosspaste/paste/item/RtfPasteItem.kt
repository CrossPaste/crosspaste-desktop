package com.crosspaste.paste.item

import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createRtfPasteItem
import com.crosspaste.paste.item.PasteItem.Companion.getExtraInfoFromJson
import com.crosspaste.paste.item.PasteItemProperties.BACKGROUND
import com.crosspaste.serializer.RtfPasteItemSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

@Serializable(with = RtfPasteItemSerializer::class)
class RtfPasteItem(
    override val identifiers: List<String>,
    override val hash: String,
    override val rtf: String,
    override val size: Long,
    override val extraInfo: JsonObject? = null,
) : PasteItem,
    PasteRtf {

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        rtf = jsonObject["rtf"]!!.jsonPrimitive.content,
        extraInfo = getExtraInfoFromJson(jsonObject),
    )

    override fun getText(): String = rtf

    override fun getHtml(): String = ""

    override fun getBackgroundColor(): Int =
        extraInfo?.let { json ->
            runCatching {
                json[BACKGROUND]?.jsonPrimitive?.int
            }.getOrNull()
        } ?: 0

    override fun bind(pasteCoordinate: PasteCoordinate): RtfPasteItem = this

    override fun copy(extraInfo: JsonObject?): RtfPasteItem =
        createRtfPasteItem(
            identifiers = identifiers,
            rtf = rtf,
            extraInfo = extraInfo,
        )

    override fun getPasteType(): PasteType = PasteType.RTF_TYPE

    override fun getSearchContent(): String = rtf.lowercase()

    override fun getSummary(): String = rtf

    override fun isValid(): Boolean =
        hash.isNotEmpty() &&
            size > 0 &&
            rtf.isNotEmpty()

    override fun toJson(): String =
        buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("hash", hash)
            put("size", size)
            put("rtf", rtf)
            extraInfo?.let { put("extraInfo", it) }
        }.toString()
}
