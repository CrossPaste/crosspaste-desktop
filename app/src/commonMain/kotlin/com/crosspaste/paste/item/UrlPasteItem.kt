package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.item.HtmlPasteItem.Companion.fileUtils
import com.crosspaste.paste.item.PasteItem.Companion.getExtraInfoFromJson
import com.crosspaste.paste.item.PasteItemProperties.TITLE
import com.crosspaste.path.UserDataPathProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okio.Path
import okio.Path.Companion.toPath

@Serializable
@SerialName("url")
class UrlPasteItem(
    override val identifiers: List<String>,
    override val hash: String,
    override val size: Long,
    override val url: String,
    override val extraInfo: JsonObject? = null,
) : PasteItem,
    PasteUrl {

    companion object {
        const val OPEN_GRAPH_IMAGE = "openGraphImage.png"
    }

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        url = jsonObject["url"]!!.jsonPrimitive.content,
        extraInfo = getExtraInfoFromJson(jsonObject),
    )

    override fun getPasteType(): PasteType = PasteType.URL_TYPE

    override fun getSearchContent(): String = url.lowercase()

    override fun getSummary(): String = url

    override fun getTitle(): String? =
        extraInfo?.let {
            it[TITLE]?.jsonPrimitive?.content
        }

    override fun getRenderingFilePath(
        pasteCoordinate: PasteCoordinate,
        userDataPathProvider: UserDataPathProvider,
    ): Path =
        getMarketingPath()?.toPath() ?: run {
            val basePath = userDataPathProvider.resolve(appFileType = AppFileType.OPEN_GRAPH)
            val relativePath =
                fileUtils.createPasteRelativePath(
                    pasteCoordinate = pasteCoordinate,
                    fileName = OPEN_GRAPH_IMAGE,
                )
            userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
        }

    override fun update(
        data: Any,
        hash: String,
    ): PasteItem =
        (data as? String)?.let { url ->
            UrlPasteItem(
                identifiers = identifiers,
                hash = hash,
                size = url.length.toLong(),
                url = url,
            )
        } ?: this

    override fun isValid(): Boolean =
        hash.isNotEmpty() &&
            size > 0 &&
            url.isNotEmpty()

    override fun toJson(): String =
        buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("hash", hash)
            put("size", size)
            put("url", url)
            extraInfo?.let { put("extraInfo", it) }
        }.toString()
}
