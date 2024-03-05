package com.clipevery.dao.clip

import com.clipevery.dao.clip.ClipContent.Companion.getClipItem
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.FullText
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class ClipData: RealmObject {
    @PrimaryKey
    var id: ObjectId = ObjectId()
    @Index
    var clipId: Int = 0
    var clipAppearContent: RealmAny? = null
    var clipContent: ClipContent? = null
    @Index
    var clipType: Int = ClipType.INVALID
    @FullText
    var clipSearchContent: String? = null
    @Index
    var md5: String = ""
    @Index
    var appInstanceId: String = ""
    @Index
    var createTime: RealmInstant = RealmInstant.now()

    var clipState: Int = ClipState.LOADING

    var labels: RealmSet<ClipLabel> = realmSetOf()

    constructor()

    constructor(
        clipId: Int,
        clipAppearContent: RealmAny?,
        clipContent: ClipContent,
        clipType: Int,
        appInstanceId: String
    ) {
        this.clipId = clipId
        this.clipAppearContent = clipAppearContent
        this.clipContent = clipContent
        this.clipType = clipType
        this.appInstanceId = appInstanceId
    }

    // must be called in writeBlocking
    fun clear(realm: MutableRealm, clearResource: Boolean = true) {
        getClipItem(clipAppearContent)?.clear(realm, clearResource)
        clipContent?.clear(realm, clearResource)
        realm.delete(this)
    }
}
