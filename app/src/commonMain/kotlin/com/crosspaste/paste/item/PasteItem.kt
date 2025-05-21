package com.crosspaste.paste.item

import com.crosspaste.db.paste.PasteType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    }

    val extraInfo: String?

    val identifiers: List<String>

    val hash: String

    val size: Long

    fun getPasteType(): PasteType

    fun getSearchContent(): String?

    fun getTitle(): String

    fun bind(pasteCoordinate: PasteCoordinate): PasteItem {
        return this
    }

    fun update(
        data: Any,
        hash: String,
    ): PasteItem

    fun clear(
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean = true,
    ) {
        // default do nothing
    }

    fun isValid(): Boolean

    fun toJson(): String
}
