package com.crosspaste.paste.item

import com.crosspaste.db.paste.PasteType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
@SerialName("url")
class UrlPasteItem(
    override val identifiers: List<String>,
    override val hash: String,
    override val size: Long,
    override val url: String,
) : PasteItem, PasteUrl {

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.content.toLong(),
        url = jsonObject["url"]!!.jsonPrimitive.content,
    )

    override fun getPasteType(): PasteType {
        return PasteType.URL_TYPE
    }

    override fun getSearchContent(): String {
        return url.lowercase()
    }

    override fun getTitle(): String {
        return url
    }

    override fun update(
        data: Any,
        hash: String,
    ): PasteItem {
        return (data as? String)?.let { url ->
            UrlPasteItem(
                identifiers = identifiers,
                hash = hash,
                size = url.length.toLong(),
                url = url,
            )
        } ?: this
    }

    override fun isValid(): Boolean {
        return hash.isNotEmpty() &&
            size > 0 &&
            url.isNotEmpty()
    }

    override fun toJson(): String {
        return buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("hash", hash)
            put("size", size)
            put("url", url)
        }.toString()
    }
}
