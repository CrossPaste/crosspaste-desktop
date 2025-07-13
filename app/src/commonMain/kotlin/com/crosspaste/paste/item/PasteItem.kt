package com.crosspaste.paste.item

import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.item.PasteItemProperties.MARKETING_PATH
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path

@Serializable
sealed interface PasteItem {

    companion object {

        private val jsonUtils = getJsonUtils()

        fun fromJson(json: String): PasteItem {
            val jsonObject = jsonUtils.JSON.parseToJsonElement(json).jsonObject
            jsonObject["type"]!!.jsonPrimitive.content.toInt().let {
                return when (it) {
                    PasteType.COLOR_TYPE.type -> ColorPasteItem(jsonObject)
                    PasteType.FILE_TYPE.type -> FilesPasteItem(jsonObject)
                    PasteType.HTML_TYPE.type -> HtmlPasteItem(jsonObject)
                    PasteType.IMAGE_TYPE.type -> ImagesPasteItem(jsonObject)
                    PasteType.RTF_TYPE.type -> RtfPasteItem(jsonObject)
                    PasteType.TEXT_TYPE.type -> TextPasteItem(jsonObject)
                    PasteType.URL_TYPE.type -> UrlPasteItem(jsonObject)
                    else -> throw IllegalArgumentException("Unknown paste type: $it")
                }
            }
        }

        // To be compatible with older versions
        // the extraInfo field may be a JsonObject or a String
        fun getExtraInfoFromJson(jsonObject: JsonObject): JsonObject? {
            val extraInfo = jsonObject["extraInfo"] ?: return null

            return when (extraInfo) {
                is JsonObject -> extraInfo
                is JsonPrimitive -> {
                    if (extraInfo.isString) {
                        runCatching {
                            jsonUtils.JSON.parseToJsonElement(extraInfo.content) as? JsonObject
                        }.getOrNull()
                    } else {
                        null
                    }
                }

                else -> null
            }
        }

        fun updateExtraInfo(
            extraInfo: JsonObject?,
            update: JsonObjectBuilder.() -> Unit,
        ): JsonObject {
            return buildJsonObject {
                extraInfo?.let { extraInfo ->
                    extraInfo.forEach { (key, value) ->
                        put(key, value)
                    }
                }
                update()
            }
        }
    }

    val extraInfo: JsonObject?

    val identifiers: List<String>

    val hash: String

    val size: Long

    fun getPasteType(): PasteType

    fun getSearchContent(): String?

    fun getSummary(): String

    fun getMarketingPath(): String? {
        return extraInfo?.let { extraInfo ->
            extraInfo[MARKETING_PATH]?.jsonPrimitive?.content
        }
    }

    fun bind(pasteCoordinate: PasteCoordinate): PasteItem {
        return this
    }

    fun getRenderingFilePath(
        pasteCoordinate: PasteCoordinate,
        userDataPathProvider: UserDataPathProvider,
    ): Path? {
        return null
    }

    fun update(
        data: Any,
        hash: String,
    ): PasteItem

    fun clear(
        clearResource: Boolean = true,
        pasteCoordinate: PasteCoordinate,
        userDataPathProvider: UserDataPathProvider,
    ) {
        // default do nothing
    }

    fun isValid(): Boolean

    fun toJson(): String
}
