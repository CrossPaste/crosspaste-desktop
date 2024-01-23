package com.clipevery.dao.clip

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
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
    var clipContents: RealmList<ClipContent> = realmListOf()
    @Index
    var clipType: Int = -1
    @FullText
    var clipSearchContent: String? = null
    @Index
    var md5: String = ""
    @Index
    var appInstanceId: String = ""
    @Index
    var createTime: RealmInstant = RealmInstant.now()

    var preCreate: Boolean = true

    var labels: RealmSet<ClipLabel> = realmSetOf()

    constructor()

    constructor(
        clipId: Int,
        clipAppearContent: RealmAny,
        clipContents: RealmList<ClipContent>,
        clipType: Int,
        appInstanceId: String
    ) {
        this.clipId = clipId
        this.clipAppearContent = clipAppearContent
        this.clipContents = clipContents
        this.clipType = clipType
        this.appInstanceId = appInstanceId
    }


}
