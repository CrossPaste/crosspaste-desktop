package com.crosspaste.db.paste

import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteText
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.serializer.PasteDataSerializer
import com.crosspaste.utils.DateUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.reflect.KClass
import kotlin.reflect.cast

@Serializable(with = PasteDataSerializer::class)
data class PasteData(
    @Transient
    val id: Long = -1L,
    val appInstanceId: String,
    val favorite: Boolean = false,
    val pasteId: Long,
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
        fun mapper(
            id: Long,
            appInstanceId: String,
            favorite: Boolean,
            pasteId: Long,
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
                pasteId,
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

    fun asSyncPasteData(): PasteData {
        val now = DateUtils.nowEpochMilliseconds()

        val pasteCoordinate = PasteCoordinate(appInstanceId, pasteId, now)

        val newPasteAppearItem = pasteAppearItem?.bind(pasteCoordinate)

        val newPasteCollection = pasteCollection.bind(pasteCoordinate)

        return PasteData(
            appInstanceId = appInstanceId,
            favorite = favorite,
            pasteId = pasteId,
            pasteAppearItem = newPasteAppearItem,
            pasteCollection = newPasteCollection,
            pasteType = pasteType,
            source = source,
            size = size,
            hash = hash,
            createTime = now,
            pasteSearchContent = createSearchContent(
                source,
                newPasteAppearItem?.getSearchContent(),
            ),
            pasteState = pasteState,
            remote = true,
        )
    }

    fun existFileResource(): Boolean {
        return getPasteAppearItems().any { it is PasteFiles }
    }

    fun getTypeText(): String {
        return PasteType.fromType(this.pasteType).name
    }

    fun getPasteCoordinate(): PasteCoordinate {
        return PasteCoordinate(appInstanceId, pasteId, createTime)
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
}
