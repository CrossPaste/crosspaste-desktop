package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createRtfPasteItem
import com.crosspaste.paste.item.PasteItem.Companion.getExtraInfoFromJson
import com.crosspaste.paste.item.PasteItemProperties.BACKGROUND
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.serializer.RtfPasteItemSerializer
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.getRtfUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okio.Path
import okio.Path.Companion.toPath

@Serializable(with = RtfPasteItemSerializer::class)
class RtfPasteItem(
    override val identifiers: List<String>,
    override val hash: String,
    override val rtf: String,
    override val size: Long,
    override val extraInfo: JsonObject? = null,
    var relativePath: String? = null,
) : PasteItem,
    PasteRtf {

    companion object {
        val fileUtils = getFileUtils()
        val jsonUtils = getJsonUtils()
        val rtfUtils = getRtfUtils()

        const val RTF2IMAGE = "rtf2Image.png"
    }

    private val rtfTextCache by lazy {
        rtfUtils.getText(rtf) ?: ""
    }

    private val rtfHtmlCache by lazy {
        rtfUtils.rtfToHtml(rtf) ?: ""
    }

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        rtf = jsonObject["rtf"]!!.jsonPrimitive.content,
        extraInfo = getExtraInfoFromJson(jsonObject),
    )

    override fun getText(): String = rtfTextCache

    override fun getHtml(): String = rtfHtmlCache

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

    override fun getSearchContent(): String = rtfTextCache.lowercase()

    override fun getSummary(): String = rtfTextCache

    override fun getRenderingFilePath(
        pasteCoordinate: PasteCoordinate,
        userDataPathProvider: UserDataPathProvider,
    ): Path =
        getMarketingPath()?.toPath() ?: run {
            val basePath = userDataPathProvider.resolve(appFileType = AppFileType.RTF)
            val relativePath =
                fileUtils.createPasteRelativePath(
                    pasteCoordinate = pasteCoordinate,
                    fileName = RTF2IMAGE,
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
            rtf.isNotEmpty() &&
            rtfTextCache.isNotEmpty() &&
            rtfHtmlCache.isNotEmpty()

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
