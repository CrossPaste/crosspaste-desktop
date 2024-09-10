package com.crosspaste.realm.paste

import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.serializer.PasteDataSerializer
import com.crosspaste.serializer.PasteLabelRealmSetSerializer
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmSetOf
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

@Serializable(with = PasteDataSerializer::class)
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
    var pasteType: Int = PasteType.INVALID

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

    var remote: Boolean = false

    @Index
    var favorite: Boolean = false

    @Serializable(with = PasteLabelRealmSetSerializer::class)
    var labels: RealmSet<PasteLabel> = realmSetOf()

    // must be called in writeBlocking
    fun clear(
        realm: MutableRealm,
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean = true,
    ) {
        PasteCollection.getPasteItem(pasteAppearItem)?.clear(realm, userDataPathProvider, clearResource)
        pasteCollection?.clear(realm, userDataPathProvider, clearResource)
        realm.delete(this)
    }

    fun getPasteDataSortObject(): PasteDataSortObject {
        return PasteDataSortObject(createTime, pasteId, appInstanceId)
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

    fun adaptRelativePaths(
        appInstanceId: String,
        pasteId: Long,
    ) {
        for (pasteAppearItem in this.getPasteAppearItems()) {
            if (pasteAppearItem is PasteFiles) {
                pasteAppearItem.adaptRelativePaths(appInstanceId, pasteId)
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
}

data class PasteDataSortObject(
    val createTime: RealmInstant,
    val pasteId: Long,
    val appInstanceId: String,
) : Comparable<PasteDataSortObject> {

    override fun compareTo(other: PasteDataSortObject): Int {
        val createTimeCompare = createTime.compareTo(other.createTime)
        if (createTimeCompare != 0) {
            return createTimeCompare
        }
        val pasteIdCompare = pasteId.compareTo(other.pasteId)
        if (pasteIdCompare != 0) {
            return pasteIdCompare
        }
        return appInstanceId.compareTo(other.appInstanceId)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasteDataSortObject) return false

        if (createTime != other.createTime) return false
        if (pasteId != other.pasteId) return false
        if (appInstanceId != other.appInstanceId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = createTime.hashCode()
        result = 31 * result + pasteId.hashCode()
        result = 31 * result + appInstanceId.hashCode()
        return result
    }
}
