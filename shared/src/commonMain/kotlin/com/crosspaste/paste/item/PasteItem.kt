package com.crosspaste.paste.item

import androidx.compose.runtime.Stable
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.PasteItemProperties.MARKETING_PATH
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path

@Serializable
@Stable
sealed interface PasteItem {

    companion object {

        private val logger = KotlinLogging.logger { }

        private val jsonUtils = getJsonUtils()

        fun fromJson(json: String): PasteItem? =
            runCatching {
                val jsonObject = jsonUtils.JSON.parseToJsonElement(json).jsonObject
                jsonObject["type"]!!.jsonPrimitive.content.toInt().let {
                    when (it) {
                        PasteType.COLOR_TYPE.type -> ColorPasteItem(jsonObject)
                        PasteType.FILE_TYPE.type -> FilesPasteItem(jsonObject)
                        PasteType.HTML_TYPE.type -> HtmlPasteItem(jsonObject)
                        PasteType.IMAGE_TYPE.type -> ImagesPasteItem(jsonObject)
                        PasteType.RTF_TYPE.type -> RtfPasteItem(jsonObject)
                        PasteType.TEXT_TYPE.type -> TextPasteItem(jsonObject)
                        PasteType.URL_TYPE.type -> UrlPasteItem(jsonObject)
                        else -> {
                            logger.warn { "Unsupported PasteItem type $it" }
                            null
                        }
                    }
                }
            }.onFailure { e ->
                logger.error(e) { "Failed to parse PasteItem from json" }
            }.getOrNull()

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
        ): JsonObject =
            buildJsonObject {
                extraInfo?.let { extraInfo ->
                    extraInfo.forEach { (key, value) ->
                        put(key, value)
                    }
                }
                update()
            }

        @Suppress("UNCHECKED_CAST")
        fun <T : PasteItem> T.copy(update: JsonObjectBuilder.() -> Unit): T {
            val newExtraInfo = updateExtraInfo(this.extraInfo, update)
            // Cast to T is safe here because withExtraInfo is expected
            // to return the same implementation type
            return this.copy(newExtraInfo) as T
        }
    }

    val extraInfo: JsonObject?

    val identifiers: List<String>

    val hash: String

    val size: Long

    fun getPasteType(): PasteType

    fun getSearchContent(): String?

    fun getUserEditName(): String? =
        extraInfo?.let { extraInfo ->
            extraInfo[PasteItemProperties.NAME]?.jsonPrimitive?.content
        }

    fun getSummary(): String

    fun getMarketingPath(): String? =
        extraInfo?.let { extraInfo ->
            extraInfo[MARKETING_PATH]?.jsonPrimitive?.content
        }

    fun bind(
        pasteCoordinate: PasteCoordinate,
        syncToDownload: Boolean = false,
    ): PasteItem = this

    fun copy(extraInfo: JsonObject? = null): PasteItem

    fun copy(update: JsonObjectBuilder.() -> Unit): PasteItem {
        val newExtraInfo = updateExtraInfo(extraInfo, update)
        return copy(extraInfo = newExtraInfo)
    }

    fun getRenderingFilePath(
        pasteCoordinate: PasteCoordinate,
        userDataPathProvider: UserDataPathProvider,
    ): Path? = null

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
