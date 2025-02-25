package com.crosspaste.db.paste

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
data class PasteCollection(
    val pasteItems: List<PasteItem>,
) {

    companion object {

        private val jsonUtils = getJsonUtils();

        fun fromJson(json: String): PasteCollection {
            val jsonArray = jsonUtils.JSON.parseToJsonElement(json).jsonArray
            return PasteCollection(
                jsonArray.map {
                    PasteItem.fromJson(it.jsonPrimitive.content)
                }
            )
        }
    }

    fun clear(
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean = true,
    ) {
        pasteItems.forEach {
            it.clear(userDataPathProvider, clearResource)
        }
    }

    fun bind(pasteCoordinate: PasteCoordinate): PasteCollection {
        return PasteCollection(pasteItems.map { it.bind(pasteCoordinate) })
    }

    fun toJson(): String {
        val jsonArray = buildJsonArray {
            for (item in pasteItems) {
                add(item.toJson())
            }
        }
        return jsonArray.toString()
    }
}
