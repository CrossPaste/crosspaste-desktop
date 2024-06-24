package com.clipevery.dao.clip

import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipCollection.Companion.getClipItem
import com.clipevery.serializer.ClipDataSerializer
import com.clipevery.serializer.ClipLabelRealmSetSerializer
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

@Serializable(with = ClipDataSerializer::class)
class ClipData : RealmObject {

    companion object {}

    @PrimaryKey
    var id: ObjectId = ObjectId()

    @Index
    var appInstanceId: String = ""

    @Index
    var clipId: Long = 0
    var clipAppearItem: RealmAny? = null
    var clipCollection: ClipCollection? = null

    @Index
    var clipType: Int = ClipType.INVALID

    var source: String? = null

    @FullText
    @Transient
    var clipSearchContent: String? = null

    var size: Long = 0

    @Index
    var md5: String = ""

    @Index
    @Transient
    var createTime: RealmInstant = RealmInstant.now()

    @Index
    @Transient
    var clipState: Int = ClipState.LOADING

    var remote: Boolean = false

    @Index
    var favorite: Boolean = false

    @Serializable(with = ClipLabelRealmSetSerializer::class)
    var labels: RealmSet<ClipLabel> = realmSetOf()

    // must be called in writeBlocking
    fun clear(
        realm: MutableRealm,
        clearResource: Boolean = true,
    ) {
        getClipItem(clipAppearItem)?.clear(realm, clearResource)
        clipCollection?.clear(realm, clearResource)
        realm.delete(this)
    }

    fun getClipDataSortObject(): ClipDataSortObject {
        return ClipDataSortObject(createTime, clipId, appInstanceId)
    }

    fun getClipAppearItems(): List<ClipItem> {
        val appearItem: ClipItem? = getClipItem(this.clipAppearItem)

        val otherAppearItems: List<ClipItem>? =
            this.clipCollection?.clipItems?.mapNotNull {
                getClipItem(it)
            }

        val mutableList: MutableList<ClipItem> = mutableListOf()

        appearItem?.let {
            mutableList.add(it)
        }

        otherAppearItems?.let {
            mutableList.addAll(it)
        }

        return mutableList.toList()
    }

    fun existFileResource(): Boolean {
        return getClipAppearItems().any { it is ClipFiles }
    }

    fun updateClipState(clipState: Int) {
        this.clipState = clipState
        for (clipAppearItem in this.getClipAppearItems()) {
            clipAppearItem.clipState = ClipState.LOADED
        }
    }
}

data class ClipDataSortObject(val createTime: RealmInstant, val clipId: Long, val appInstanceId: String) : Comparable<ClipDataSortObject> {

    override fun compareTo(other: ClipDataSortObject): Int {
        val createTimeCompare = createTime.compareTo(other.createTime)
        if (createTimeCompare != 0) {
            return createTimeCompare
        }
        val clipIdCompare = clipId.compareTo(other.clipId)
        if (clipIdCompare != 0) {
            return clipIdCompare
        }
        return appInstanceId.compareTo(other.appInstanceId)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClipDataSortObject) return false

        if (createTime != other.createTime) return false
        if (clipId != other.clipId) return false
        if (appInstanceId != other.appInstanceId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = createTime.hashCode()
        result = 31 * result + clipId.hashCode()
        result = 31 * result + appInstanceId.hashCode()
        return result
    }
}
