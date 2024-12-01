package com.crosspaste.realm.paste

import com.crosspaste.dto.paste.SyncPasteData
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteCoordinateBinder
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteText
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.serializer.PasteLabelRealmSetSerializer
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.FullText
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.mongodb.kbson.ObjectId
import kotlin.reflect.KClass
import kotlin.reflect.cast

class PasteData : RealmObject {

    companion object {

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

        fun toSyncPasteData(pasteData: PasteData): SyncPasteData {
            return SyncPasteData(
                id = pasteData.id.toHexString(),
                appInstanceId = pasteData.appInstanceId,
                pasteId = pasteData.pasteId,
                pasteType = pasteData.pasteType,
                source = pasteData.source,
                size = pasteData.size,
                hash = pasteData.hash,
                favorite = pasteData.favorite,
                pasteAppearItem = PasteCollection.getPasteItem(pasteData.pasteAppearItem),
                pasteCollection = pasteData.pasteCollection?.let { PasteCollection.toSyncPasteCollection(it) },
                labels = pasteData.labels.map { PasteLabel.toSyncPasteLabel(it) }.toSet(),
            )
        }

        fun fromSyncPasteData(syncPasteData: SyncPasteData): PasteData {
            return PasteData().apply {
                this.id = ObjectId(syncPasteData.id)
                this.appInstanceId = syncPasteData.appInstanceId
                this.pasteId = syncPasteData.pasteId
                this.pasteType = syncPasteData.pasteType
                this.source = syncPasteData.source
                this.size = syncPasteData.size
                this.hash = syncPasteData.hash
                this.favorite = syncPasteData.favorite
                this.pasteAppearItem =
                    syncPasteData.pasteAppearItem?.let {
                        RealmAny.create(it as RealmObject)
                    }
                this.pasteCollection =
                    syncPasteData.pasteCollection?.let {
                        PasteCollection.fromSyncPasteCollection(it)
                    }
                this.labels =
                    syncPasteData.labels.map {
                        PasteLabel.fromSyncPasteLabel(it)
                    }.toRealmSet()

                this.remote = true

                this.pasteSearchContent =
                    createSearchContent(
                        syncPasteData.source,
                        PasteCollection.getPasteItem(this.pasteAppearItem)?.getSearchContent(),
                    )

                for (pasteBinder in this.getPasteAppearItems().filterIsInstance<PasteCoordinateBinder>()) {
                    pasteBinder.bind(this.getPasteCoordinate())
                }
            }
        }
    }

    @PrimaryKey
    var id: ObjectId = ObjectId()

    @Index
    var appInstanceId: String = ""

    @Index
    var pasteId: Long = 0
    var pasteAppearItem: RealmAny? = null
    var pasteCollection: PasteCollection? = null

    @Index
    var pasteType: Int = PasteType.INVALID_TYPE.type

    var source: String? = null

    @FullText
    @Transient
    var pasteSearchContent: String? = null

    var size: Long = 0

    @Index
    var hash: String = ""

    @Index
    @Transient
    var createTime: RealmInstant = RealmInstant.now()

    @Index
    @Transient
    var pasteState: Int = PasteState.LOADING

    @Transient
    var remote: Boolean = false

    @Index
    var favorite: Boolean = false

    @Serializable(with = PasteLabelRealmSetSerializer::class)
    var labels: RealmSet<PasteLabel> = realmSetOf()

    fun getType(): PasteType {
        return PasteType.fromType(pasteType)
    }

    fun clear(
        realm: MutableRealm,
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean = true,
    ) {
        PasteCollection.getPasteItem(pasteAppearItem)?.clear(realm, userDataPathProvider, clearResource)
        pasteCollection?.clear(realm, userDataPathProvider, clearResource)
        realm.delete(this)
    }

    fun getPasteAppearItems(): List<PasteItem> {
        val appearItem: PasteItem? = PasteCollection.getPasteItem(this.pasteAppearItem)

        val otherAppearItems: List<PasteItem>? =
            this.pasteCollection?.pasteItems?.mapNotNull {
                PasteCollection.getPasteItem(it)
            }

        val mutableList: MutableList<PasteItem> = mutableListOf()

        appearItem?.let {
            mutableList.add(it)
        }

        otherAppearItems?.let {
            mutableList.addAll(it)
        }

        return mutableList.toList()
    }

    fun existFileResource(): Boolean {
        return getPasteAppearItems().any { it is PasteFiles }
    }

    fun adaptRelativePaths(pasteCoordinate: PasteCoordinate) {
        for (pasteAppearItem in this.getPasteAppearItems()) {
            if (pasteAppearItem is PasteFiles) {
                pasteAppearItem.adaptRelativePaths(pasteCoordinate)
            }
        }
    }

    fun updatePasteState(pasteState: Int) {
        this.pasteState = pasteState
        for (pasteAppearItem in this.getPasteAppearItems()) {
            pasteAppearItem.pasteState = PasteState.LOADED
        }
    }

    fun <T : Any> getPasteItem(kclass: KClass<T>): T? {
        return PasteCollection.getPasteItem(this.pasteAppearItem)?.let {
            if (kclass.isInstance(it)) {
                kclass.cast(it)
            } else {
                null
            }
        }
    }

    fun getPasteItem(): PasteItem? {
        return PasteCollection.getPasteItem(this.pasteAppearItem)
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
                    this.getPasteItem()?.getTitle() ?: "Unknown"
                }
                PasteType.HTML_TYPE,
                PasteType.RTF_TYPE,
                -> {
                    getPasteAppearItems().firstOrNull { it is PasteText }?.let {
                        val pasteText = it as PasteText
                        return pasteText.text.trim()
                    } ?: run {
                        getPasteItem()?.getTitle() ?: "Unknown"
                    }
                }
                else -> {
                    "Unknown"
                }
            }
        }
    }

    fun getTypeText(): String {
        return PasteType.fromType(this.pasteType).name
    }
}
