package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.item.PasteItem.Companion.getExtraInfoFromJson
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.serializer.RtfPasteItemSerializer
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.getRtfUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
) : PasteItem, PasteRtf {

    companion object {
        val fileUtils = getFileUtils()
        val jsonUtils = getJsonUtils()
        val rtfUtils = getRtfUtils()

        const val RTF2IMAGE = "rtf2Image.png"
    }

    private val rtfTextCache by lazy {
        rtfUtils.getRtfText(rtf)
    }

    constructor(jsonObject: JsonObject) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        rtf = jsonObject["rtf"]!!.jsonPrimitive.content,
        extraInfo = getExtraInfoFromJson(jsonObject),
    )

    override fun bind(pasteCoordinate: PasteCoordinate): RtfPasteItem {
        return RtfPasteItem(
            identifiers = identifiers,
            hash = hash,
            size = size,
            rtf = rtf,
            extraInfo = extraInfo,
        )
    }

    override fun getPasteType(): PasteType {
        return PasteType.RTF_TYPE
    }

    override fun getSearchContent(): String {
        return rtfTextCache.lowercase()
    }

    override fun getSummary(): String {
        return rtfTextCache
    }

    override fun update(
        data: Any,
        hash: String,
    ): PasteItem {
        return (data as? String)?.let { rtf ->
            // todo update rtf image
            RtfPasteItem(
                identifiers = identifiers,
                hash = hash,
                size = rtf.encodeToByteArray().size.toLong(),
                rtf = rtf,
                extraInfo = extraInfo,
            )
        } ?: this
    }

    override fun getRenderingFilePath(
        pasteCoordinate: PasteCoordinate,
        userDataPathProvider: UserDataPathProvider,
    ): Path {
        return getMarketingPath()?.toPath() ?: run {
            val basePath = userDataPathProvider.resolve(appFileType = AppFileType.RTF)
            val relativePath =
                fileUtils.createPasteRelativePath(
                    pasteCoordinate = pasteCoordinate,
                    fileName = RTF2IMAGE,
                )
            userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
        }
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

    override fun isValid(): Boolean {
        return hash.isNotEmpty() &&
            size > 0 &&
            rtf.isNotEmpty()
    }

    override fun toJson(): String {
        return buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("hash", hash)
            put("size", size)
            put("rtf", rtf)
            extraInfo?.let { put("extraInfo", it) }
        }.toString()
    }
}
