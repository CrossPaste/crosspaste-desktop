package com.crosspaste.paste

import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class PasteCollection(
    val pasteItems: List<PasteItem>,
) {

    companion object {

        private val logger = KotlinLogging.logger { }

        private val jsonUtils = getJsonUtils()

        fun fromStoredJson(json: String): PasteCollection {
            val jsonArray = jsonUtils.JSON.parseToJsonElement(json).jsonArray
            return PasteCollection(
                jsonArray.mapIndexed { index, element ->
                    requireNotNull(PasteItem.fromStoredJson(element.jsonPrimitive.content)) {
                        "Unsupported or invalid stored PasteItem at index $index"
                    }
                },
            )
        }

        internal fun fromStoredJsonTolerant(json: String): PasteCollection {
            val jsonArray =
                runCatching {
                    jsonUtils.JSON.parseToJsonElement(json).jsonArray
                }.onFailure { error ->
                    logger.error(error) { "Failed to parse stored PasteCollection; using an empty collection" }
                }.getOrElse {
                    return PasteCollection(emptyList())
                }

            return PasteCollection(
                jsonArray.mapIndexedNotNull { index, element ->
                    val item =
                        runCatching {
                            PasteItem.fromStoredJson(element.jsonPrimitive.content)
                        }.onFailure { error ->
                            logger.error(error) { "Failed to decode stored PasteItem at index $index; skipping it" }
                        }.getOrNull()

                    if (item == null) {
                        logger.error { "Unsupported or invalid stored PasteItem at index $index; skipping it" }
                    }
                    item
                },
            )
        }

        @Deprecated("Use fromStoredJson for the legacy database/import format")
        fun fromJson(json: String): PasteCollection = fromStoredJson(json)
    }

    fun bind(
        pasteCoordinate: PasteCoordinate,
        syncToDownload: Boolean = false,
    ): PasteCollection =
        PasteCollection(
            pasteItems.map {
                it.bind(pasteCoordinate, syncToDownload)
            },
        )

    fun toStoredJson(): String {
        val jsonArray =
            buildJsonArray {
                for (item in pasteItems) {
                    add(item.toStoredJson())
                }
            }
        return jsonArray.toString()
    }

    @Deprecated("Use toStoredJson for the legacy database/import format")
    fun toJson(): String = toStoredJson()
}
