package com.clipevery.dao.clip

import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipContent.Companion.getClipItem
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
class ClipData: RealmObject {
    @PrimaryKey
    @Transient
    var id: ObjectId = ObjectId()
    @Index
    var clipId: Long = 0
    var clipAppearContent: RealmAny? = null
    var clipContent: ClipContent? = null
    @Index
    var clipType: Int = ClipType.INVALID
    @FullText
    @Transient
    var clipSearchContent: String? = null
    @Index
    var md5: String = ""
    @Index
    var appInstanceId: String = ""
    @Index
    @Transient
    var createTime: RealmInstant = RealmInstant.now()

    @Transient
    var clipState: Int = ClipState.LOADING

    var isRemote: Boolean = false

    @Serializable(with = ClipLabelRealmSetSerializer::class)
    var labels: RealmSet<ClipLabel> = realmSetOf()

    // must be called in writeBlocking
    fun clear(realm: MutableRealm, clearResource: Boolean = true) {
        getClipItem(clipAppearContent)?.clear(realm, clearResource)
        clipContent?.clear(realm, clearResource)
        realm.delete(this)
    }

    fun getClipDataHashObject(): ClipDataHashObject {
        return ClipDataHashObject(clipId, appInstanceId)
    }

    fun getClipAppearItems(): List<ClipAppearItem> {
        val appearItem: ClipAppearItem? = getClipItem(this.clipAppearContent)

        val otherAppearItems: List<ClipAppearItem>? = this.clipContent?.clipAppearItems?.mapNotNull {
            getClipItem(it)
        }

        val mutableList: MutableList<ClipAppearItem> = mutableListOf()

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
}

data class ClipDataHashObject(val clipId: Long, val appInstanceId: String): Comparable<ClipDataHashObject> {
    override fun compareTo(other: ClipDataHashObject): Int {
        val clipIdCompare = clipId.compareTo(other.clipId)
        if (clipIdCompare != 0) {
            return clipIdCompare
        }
        return appInstanceId.compareTo(other.appInstanceId)
    }

}