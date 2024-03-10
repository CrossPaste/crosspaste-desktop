@file:UseSerializers(
    MutableRealmIntKSerializer::class,
    RealmAnyKSerializer::class,
    RealmDictionaryKSerializer::class,
    RealmInstantKSerializer::class,
    RealmListKSerializer::class,
    RealmSetKSerializer::class,
    RealmUUIDKSerializer::class
)

package com.clipevery.dao.clip

import com.clipevery.dao.clip.ClipContent.Companion.getClipItem
import com.clipevery.serializer.RealmInstantSerializer
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.serializers.MutableRealmIntKSerializer
import io.realm.kotlin.serializers.RealmAnyKSerializer
import io.realm.kotlin.serializers.RealmDictionaryKSerializer
import io.realm.kotlin.serializers.RealmInstantKSerializer
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.serializers.RealmSetKSerializer
import io.realm.kotlin.serializers.RealmUUIDKSerializer
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.FullText
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import org.mongodb.kbson.ObjectId

@Serializable
class ClipData: RealmObject {
    @PrimaryKey
    @Transient
    var id: ObjectId = ObjectId()
    @Index
    var clipId: Int = 0
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
    @Serializable(with = RealmInstantSerializer::class)
    var createTime: RealmInstant = RealmInstant.now()

    @Transient
    var clipState: Int = ClipState.LOADING

    var labels: RealmSet<ClipLabel> = realmSetOf()

    constructor()

    // must be called in writeBlocking
    fun clear(realm: MutableRealm, clearResource: Boolean = true) {
        getClipItem(clipAppearContent)?.clear(realm, clearResource)
        clipContent?.clear(realm, clearResource)
        realm.delete(this)
    }

    fun getClipDataHashObject(): ClipDataHashObject {
        return ClipDataHashObject(createTime, clipId, appInstanceId)
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
}

data class ClipDataHashObject(val createTime: RealmInstant, val clipId: Int, val appInstanceId: String): Comparable<ClipDataHashObject> {
    override fun compareTo(other: ClipDataHashObject): Int {
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

}