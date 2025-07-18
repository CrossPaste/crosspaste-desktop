package com.crosspaste.paste.item

import androidx.compose.ui.graphics.Color
import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.item.PasteItem.Companion.getExtraInfoFromJson
import com.crosspaste.paste.item.PasteItemProperties.BACKGROUND
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.serializer.HtmlPasteItemSerializer
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getHtmlUtils
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okio.Path
import okio.Path.Companion.toPath

@Serializable(with = HtmlPasteItemSerializer::class)
class HtmlPasteItem(
    override val identifiers: List<String>,
    override val hash: String,
    override val html: String,
    override val size: Long,
    override val extraInfo: JsonObject? = null,
    var relativePath: String? = null,
) : PasteItem,
    PasteHtml {

    companion object {
        val fileUtils = getFileUtils()
        val jsonUtils = getJsonUtils()
        val htmlUtils = getHtmlUtils()

        const val HTML2IMAGE = "html2Image.png"
    }

    private val htmlTextCache by lazy {
        htmlUtils.getHtmlText(html)
    }

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        html = jsonObject["html"]!!.jsonPrimitive.content,
        extraInfo = getExtraInfoFromJson(jsonObject),
    )

    override fun getBackgroundColor(): Color? =
        extraInfo?.let { json ->
            runCatching {
                json[BACKGROUND]?.jsonPrimitive?.int?.let { Color(it) }
            }.getOrNull()
        }

    override fun bind(pasteCoordinate: PasteCoordinate): HtmlPasteItem =
        HtmlPasteItem(
            identifiers = identifiers,
            hash = hash,
            size = size,
            html = html,
            extraInfo = extraInfo,
        )

    override fun getPasteType(): PasteType = PasteType.HTML_TYPE

    override fun getSearchContent(): String = htmlTextCache.lowercase()

    override fun getSummary(): String = htmlTextCache

    override fun update(
        data: Any,
        hash: String,
    ): PasteItem =
        (data as? String)?.let { html ->
            // todo update html image
            HtmlPasteItem(
                identifiers = identifiers,
                hash = hash,
                size = html.encodeToByteArray().size.toLong(),
                html = html,
                extraInfo = extraInfo,
            )
        } ?: this

    override fun getRenderingFilePath(
        pasteCoordinate: PasteCoordinate,
        userDataPathProvider: UserDataPathProvider,
    ): Path =
        getMarketingPath()?.toPath() ?: run {
            val basePath = userDataPathProvider.resolve(appFileType = AppFileType.HTML)
            val relativePath =
                fileUtils.createPasteRelativePath(
                    pasteCoordinate = pasteCoordinate,
                    fileName = HTML2IMAGE,
                )
            userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
        }

    override fun clear(
        clearResource: Boolean,
        pasteCoordinate: PasteCoordinate,
        userDataPathProvider: UserDataPathProvider,
    ) {
        if (clearResource) {
            getRenderingFilePath(
                pasteCoordinate = pasteCoordinate,
                userDataPathProvider = userDataPathProvider,
            ).also { resourceFilePath ->
                fileUtils.deleteFile(resourceFilePath)
            }
        }
    }

    override fun isValid(): Boolean =
        hash.isNotEmpty() &&
            size > 0 &&
            html.isNotEmpty()

    override fun toJson(): String =
        buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("hash", hash)
            put("size", size)
            put("html", html)
            extraInfo?.let { put("extraInfo", it) }
        }.toString()
}
