package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getHtmlUtils
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okio.Path

@Serializable
@SerialName("html")
class HtmlPasteItem(
    override val identifiers: List<String>,
    override val hash: String,
    override val size: Long,
    override val relativePath: String,
    override val html: String,
) : PasteItem, PasteHtml {

    companion object {
        val fileUtils = getFileUtils()
        val jsonUtils = getJsonUtils()
        val htmlUtils = getHtmlUtils()
    }

    private val htmlTextCache by lazy {
        htmlUtils.getHtmlText(html)
    }

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        relativePath = jsonObject["relativePath"]!!.jsonPrimitive.content,
        html = jsonObject["html"]!!.jsonPrimitive.content,
    )

    override fun getHtmlImagePath(userDataPathProvider: UserDataPathProvider): Path {
        val basePath = userDataPathProvider.resolve(appFileType = AppFileType.HTML)
        return userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
    }

    override fun bind(pasteCoordinate: PasteCoordinate): HtmlPasteItem {
        return HtmlPasteItem(
            identifiers = identifiers,
            hash = hash,
            size = size,
            relativePath =
                fileUtils.createPasteRelativePath(
                    pasteCoordinate = pasteCoordinate,
                    fileName = "html2Image.png",
                ),
            html = html,
        )
    }

    override fun getPasteType(): PasteType {
        return PasteType.HTML_TYPE
    }

    override fun getSearchContent(): String {
        return htmlTextCache.lowercase()
    }

    override fun getTitle(): String {
        return htmlTextCache
    }

    override fun update(
        data: Any,
        hash: String,
    ): PasteItem {
        return (data as? String)?.let { html ->
            HtmlPasteItem(
                identifiers = identifiers,
                hash = hash,
                size = html.encodeToByteArray().size.toLong(),
                relativePath = relativePath,
                html = html,
            )
        } ?: this
    }

    override fun isValid(): Boolean {
        return hash.isNotEmpty() &&
            relativePath.isNotEmpty() &&
            size > 0 &&
            html.isNotEmpty()
    }

    override fun toJson(): String {
        return buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("hash", hash)
            put("size", size)
            put("relativePath", relativePath)
            put("html", html)
        }.toString()
    }
}
