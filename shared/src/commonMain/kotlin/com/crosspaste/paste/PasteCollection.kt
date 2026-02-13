package com.crosspaste.paste

import androidx.compose.runtime.Stable
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Serializable
@Stable
data class PasteCollection(
    val pasteItems: List<PasteItem>,
) {

    companion object {

        private val jsonUtils = getJsonUtils()

        fun fromJson(json: String): PasteCollection {
            val jsonArray = jsonUtils.JSON.parseToJsonElement(json).jsonArray
            return PasteCollection(
                jsonArray.mapNotNull {
                    PasteItem.fromJson(it.jsonPrimitive.content)
                },
            )
        }
    }

    fun clear(
        clearResource: Boolean = true,
        pasteCoordinate: PasteCoordinate,
        userDataPathProvider: UserDataPathProvider,
    ) {
        pasteItems.forEach {
            it.clear(clearResource, pasteCoordinate, userDataPathProvider)
        }
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

    fun toJson(): String {
        val jsonArray =
            buildJsonArray {
                for (item in pasteItems) {
                    add(item.toJson())
                }
            }
        return jsonArray.toString()
    }
}
