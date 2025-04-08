package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.getRtfUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okio.Path

@Serializable
@SerialName("rtf")
data class RtfPasteItem(
    override val identifiers: List<String>,
    override val hash: String,
    override val size: Long,
    override val relativePath: String,
    override val rtf: String,
) : PasteItem, PasteRtf {

    companion object {
        val fileUtils = getFileUtils()
        val jsonUtils = getJsonUtils()
        val rtfUtils = getRtfUtils()
    }

    private val rtfTextCache by lazy {
        rtfUtils.getRtfText(rtf)
    }

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        relativePath = jsonObject["relativePath"]!!.jsonPrimitive.content,
        rtf = jsonObject["rtf"]!!.jsonPrimitive.content,
    )

    override fun getRtfImagePath(userDataPathProvider: UserDataPathProvider): Path {
        val basePath = userDataPathProvider.resolve(appFileType = AppFileType.RTF)
        return userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
    }

    override fun bind(pasteCoordinate: PasteCoordinate): RtfPasteItem {
        return RtfPasteItem(
            identifiers = identifiers,
            hash = hash,
            size = size,
            rtf = rtf,
            relativePath =
                HtmlPasteItem.fileUtils.createPasteRelativePath(
                    pasteCoordinate = pasteCoordinate,
                    fileName = "rtf2Image.png",
                ),
        )
    }

    override fun getPasteType(): PasteType {
        return PasteType.RTF_TYPE
    }

    override fun getSearchContent(): String {
        return rtfTextCache.lowercase()
    }

    override fun getTitle(): String {
        return rtfTextCache
    }

    override fun update(
        data: Any,
        hash: String,
    ): PasteItem {
        return (data as? String)?.let { rtf ->
            RtfPasteItem(
                identifiers = identifiers,
                hash = hash,
                size = rtf.encodeToByteArray().size.toLong(),
                rtf = rtf,
                relativePath = relativePath,
            )
        } ?: this
    }

    override fun isValid(): Boolean {
        return hash.isNotEmpty() &&
            size > 0 &&
            relativePath.isNotEmpty() &&
            rtf.isNotEmpty()
    }

    override fun toJson(): String {
        return buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("hash", hash)
            put("size", size)
            put("relativePath", relativePath)
            put("rtf", rtf)
        }.toString()
    }
}
