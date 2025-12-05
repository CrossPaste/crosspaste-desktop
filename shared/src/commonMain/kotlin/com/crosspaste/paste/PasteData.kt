package com.crosspaste.paste

import androidx.compose.runtime.Stable
import com.crosspaste.paste.item.PasteColor
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.paste.item.PasteImages
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.serializer.PasteDataSerializer
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
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
@Stable
data class PasteData(
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

        private val logger = KotlinLogging.logger { }

        private val jsonUtils = getJsonUtils()

        fun fromJson(json: String): PasteData? =
            runCatching {
                val jsonObject = jsonUtils.JSON.parseToJsonElement(json).jsonObject
                PasteData(
                    appInstanceId = jsonObject["appInstanceId"]!!.jsonPrimitive.content,
                    favorite = jsonObject["favorite"]!!.jsonPrimitive.boolean,
                    pasteAppearItem =
                        jsonObject["pasteAppearItem"]?.let {
                            PasteItem.fromJson(it.jsonPrimitive.content)
                        },
                    pasteCollection =
                        PasteCollection.fromJson(
                            jsonObject["pasteCollection"]!!.jsonPrimitive.content,
                        ),
                    pasteType = jsonObject["pasteType"]!!.jsonPrimitive.long.toInt(),
                    source = jsonObject["source"]?.jsonPrimitive?.content,
                    size = jsonObject["size"]!!.jsonPrimitive.long,
                    hash = jsonObject["hash"]!!.jsonPrimitive.content,
                )
            }.onFailure { e ->
                logger.error(e) { "Error while parsing PasteData" }
            }.getOrNull()

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
        ): PasteData =
            PasteData(
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

        fun createSearchContent(
            source: String?,
            pasteItemSearchContent: String?,
        ): String? =
            source?.let {
                pasteItemSearchContent?.let {
                    "${source.lowercase()} $pasteItemSearchContent"
                } ?: source.lowercase()
            } ?: pasteItemSearchContent
    }

    fun getType(): PasteType = PasteType.fromType(pasteType)

    fun clear(userDataPathProvider: UserDataPathProvider) {
        val pasteCoordinate = PasteCoordinate(id, appInstanceId, createTime)
        pasteAppearItem?.clear(
            pasteCoordinate = pasteCoordinate,
            userDataPathProvider = userDataPathProvider,
        )
        pasteCollection.clear(
            pasteCoordinate = pasteCoordinate,
            userDataPathProvider = userDataPathProvider,
        )
    }

    fun <T : Any> getPasteItem(clazz: KClass<T>): T? =
        pasteAppearItem?.let {
            if (clazz.isInstance(pasteAppearItem)) {
                clazz.cast(it)
            } else {
                null
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

    fun isValid(): Boolean =
        if (pasteState == PasteState.LOADED) {
            val pasteItem =
                when (getType()) {
                    PasteType.TEXT_TYPE -> getPasteItem(PasteText::class)
                    PasteType.COLOR_TYPE -> getPasteItem(PasteColor::class)
                    PasteType.URL_TYPE -> getPasteItem(PasteUrl::class)
                    PasteType.HTML_TYPE -> getPasteItem(PasteHtml::class)
                    PasteType.RTF_TYPE -> getPasteItem(PasteRtf::class)
                    PasteType.IMAGE_TYPE -> getPasteItem(PasteImages::class)
                    PasteType.FILE_TYPE -> getPasteItem(PasteFiles::class)
                    else -> null
                }
            (pasteItem as? PasteItem)?.isValid() ?: false
        } else {
            true
        }

    fun existFileCategory(): Boolean = getPasteAppearItems().any { it is PasteFiles }

    fun getTypeName(): String = PasteType.fromType(this.pasteType).name

    fun getPasteCoordinate(id: Long? = null): PasteCoordinate =
        PasteCoordinate(id ?: this.id, appInstanceId, createTime)

    fun getSummary(
        loading: String,
        unknown: String,
    ): String =
        if (this.pasteState == PasteState.LOADING) {
            loading
        } else {
            val type = PasteType.fromType(this.pasteType)
            when (type) {
                PasteType.TEXT_TYPE,
                PasteType.COLOR_TYPE,
                PasteType.URL_TYPE,
                PasteType.FILE_TYPE,
                PasteType.IMAGE_TYPE,
                -> {
                    this.pasteAppearItem?.getSummary() ?: unknown
                }
                PasteType.HTML_TYPE,
                PasteType.RTF_TYPE,
                -> {
                    getPasteAppearItems().firstOrNull { it is PasteText }?.let {
                        val pasteText = it as PasteText
                        pasteText.text.trim()
                    } ?: run {
                        pasteAppearItem?.getSummary() ?: unknown
                    }
                }
                else -> {
                    unknown
                }
            }
        }

    fun toJson(): String =
        buildJsonObject {
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
