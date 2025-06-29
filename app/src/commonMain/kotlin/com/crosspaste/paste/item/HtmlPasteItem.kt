package com.crosspaste.paste.item

import androidx.compose.ui.graphics.Color
import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getHtmlUtils
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okio.Path
import okio.Path.Companion.toPath

@Serializable
@SerialName("html")
class HtmlPasteItem(
    override val identifiers: List<String>,
    override val hash: String,
    override val size: Long,
    @Transient
    override val basePath: String? = null,
    override val relativePath: String,
    override val html: String,
    override val extraInfo: String? = null,
) : PasteItem, PasteHtml {

    companion object {
        val fileUtils = getFileUtils()
        val jsonUtils = getJsonUtils()
        val htmlUtils = getHtmlUtils()

        const val BACKGROUND_PROPERTY = "background"

        const val HTML2IMAGE = "html2Image.png"
    }

    private val htmlTextCache by lazy {
        htmlUtils.getHtmlText(html)
    }

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        basePath = jsonObject["basePath"]?.jsonPrimitive?.content,
        relativePath = jsonObject["relativePath"]!!.jsonPrimitive.content,
        html = jsonObject["html"]!!.jsonPrimitive.content,
        extraInfo = jsonObject["extraInfo"]?.jsonPrimitive?.content,
    )

    override fun getBackgroundColor(): Color? {
        return extraInfo?.let { json ->
            runCatching {
                val jsonObject = jsonUtils.JSON.parseToJsonElement(json).jsonObject
                jsonObject[BACKGROUND_PROPERTY]?.jsonPrimitive?.long?.let { Color(it) }
            }.getOrNull()
        }
    }

    override fun getHtmlImagePath(userDataPathProvider: UserDataPathProvider): Path {
        val basePath = basePath?.toPath() ?: userDataPathProvider.resolve(appFileType = AppFileType.HTML)
        return userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
    }

    override fun bind(pasteCoordinate: PasteCoordinate): HtmlPasteItem {
        return HtmlPasteItem(
            identifiers = identifiers,
            hash = hash,
            size = size,
            basePath = basePath,
            relativePath =
                fileUtils.createPasteRelativePath(
                    pasteCoordinate = pasteCoordinate,
                    fileName = HTML2IMAGE,
                ),
            html = html,
            extraInfo = extraInfo,
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
            // todo update html image
            HtmlPasteItem(
                identifiers = identifiers,
                hash = hash,
                size = html.encodeToByteArray().size.toLong(),
                basePath = basePath,
                relativePath = relativePath,
                html = html,
            )
        } ?: this
    }

    override fun clear(
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean,
    ) {
        if (clearResource) {
            if (basePath == null) {
                val basePath = userDataPathProvider.resolve(appFileType = AppFileType.HTML)
                val htmlFile =
                    userDataPathProvider.resolve(
                        basePath,
                        relativePath,
                        autoCreate = false,
                        isFile = true,
                    )
                fileUtils.deleteFile(htmlFile)
            }
        }
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
            basePath?.let { put("basePath", it) }
            put("relativePath", relativePath)
            put("html", html)
            extraInfo?.let { put("extraInfo", it) }
        }.toString()
    }
}
