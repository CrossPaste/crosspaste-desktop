package com.crosspaste.db.paste

import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteText
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.serializer.PasteDataSerializer
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.reflect.KClass
import kotlin.reflect.cast

@Serializable(with = PasteDataSerializer::class)
data class PasteData(
    @Transient
    val id: Long = -1L,
    val appInstanceId: String,
    val favorite: Boolean = false,
    val pasteAppearItem: PasteItem? = null,
    val pasteCollection: PasteCollection,
    val pasteType: Int,
    val source: String? = null,
    val size: Long,
    val hash: String,
    @Transient
    val createTime: Long = DateUtils.nowEpochMilliseconds(),
    @Transient
    val pasteSearchContent: String? = null,
    @Transient
    val pasteState: Int = PasteState.LOADING,
    @Transient
    val remote: Boolean = false,
) {

    companion object {

        private val jsonUtils = getJsonUtils()

        fun fromJson(json: String): PasteData {
            val jsonObject = jsonUtils.JSON.parseToJsonElement(json).jsonObject
            return PasteData(
                appInstanceId = jsonObject["appInstanceId"]!!.jsonPrimitive.content,
                favorite = jsonObject["favorite"]!!.jsonPrimitive.boolean,
                pasteAppearItem = jsonObject["pasteAppearItem"]?.let {
                    PasteItem.fromJson(it.jsonPrimitive.content)
                },
                pasteCollection = PasteCollection.fromJson(
                    jsonObject["pasteCollection"]!!.jsonPrimitive.content
                ),
                pasteType = jsonObject["pasteType"]!!.jsonPrimitive.long.toInt(),
                source = jsonObject["source"]?.jsonPrimitive?.content,
                size = jsonObject["size"]!!.jsonPrimitive.long,
                hash = jsonObject["hash"]!!.jsonPrimitive.content,
            )
        }

        fun mapper(
            id: Long,
            appInstanceId: String,
            favorite: Boolean,
            pasteAppearItem: String?,
            pasteCollection: String,
            pasteType: Long,
            source: String?,
            size: Long,
            hash: String,
            createTime: Long,
            pasteSearchContent: String?,
            pasteState: Long,
            remote: Boolean,
        ): PasteData {
            return PasteData(
                id,
                appInstanceId,
                favorite,
                pasteAppearItem?.let { PasteItem.fromJson(it) },
                PasteCollection.fromJson(pasteCollection),
                pasteType.toInt(),
                source,
                size,
                hash,
                createTime,
                pasteSearchContent,
                pasteState.toInt(),
                remote,
            )
        }

        fun createSearchContent(
            source: String?,
            pasteItemSearchContent: String?,
        ): String? {
            return source?.let {
                pasteItemSearchContent?.let {
                    "${source.lowercase()} $pasteItemSearchContent"
                } ?: source.lowercase()
            } ?: pasteItemSearchContent
        }
    }

    fun getType(): PasteType {
        return PasteType.fromType(pasteType)
    }

    fun clear(userDataPathProvider: UserDataPathProvider) {
        pasteAppearItem?.clear(userDataPathProvider)
        pasteCollection.clear(userDataPathProvider)
    }

    fun <T : Any> getPasteItem(clazz: KClass<T>): T? {
        return pasteAppearItem?.let {
            if (clazz.isInstance(pasteAppearItem)) {
                clazz.cast(it)
            } else {
                null
            }
        }
    }

    fun getPasteAppearItems(): List<PasteItem> {
        val mutableList: MutableList<PasteItem> = mutableListOf()

        pasteAppearItem?.let {
            mutableList.add(it)
        }

        mutableList.addAll(pasteCollection.pasteItems)

        return mutableList.toList()
    }

    fun existFileResource(): Boolean {
        return getPasteAppearItems().any { it is PasteFiles }
    }

    fun getTypeText(): String {
        return PasteType.fromType(this.pasteType).name
    }

    fun getPasteCoordinate(): PasteCoordinate {
        return PasteCoordinate(id, appInstanceId, createTime)
    }

    fun getTitle(): String {
        return if (this.pasteState == PasteState.LOADING) {
            "Loading..."
        } else {
            val type = PasteType.fromType(this.pasteType)
            when (type) {
                PasteType.TEXT_TYPE,
                PasteType.COLOR_TYPE,
                PasteType.URL_TYPE,
                PasteType.FILE_TYPE,
                PasteType.IMAGE_TYPE,
                    -> {
                    this.pasteAppearItem?.getTitle() ?: "Unknown"
                }
                PasteType.HTML_TYPE,
                PasteType.RTF_TYPE,
                    -> {
                    getPasteAppearItems().firstOrNull { it is PasteText }?.let {
                        val pasteText = it as PasteText
                        return pasteText.text.trim()
                    } ?: run {
                        pasteAppearItem?.getTitle() ?: "Unknown"
                    }
                }
                else -> {
                    "Unknown"
                }
            }
        }
    }

    fun toJson(): String {
        return buildJsonObject {
            put("appInstanceId", appInstanceId)
            put("favorite", favorite)
            pasteAppearItem?.toJson()?.let { put("pasteAppearItem", it) }
            put("pasteCollection", pasteCollection.toJson())
            put("pasteType", pasteType)
            source?.let { put("source", it) }
            put("size", size)
            put("hash", hash)
        }.toString()
    }
}
