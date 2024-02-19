package com.clipevery.dao.clip

import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmInstant
import org.mongodb.kbson.ObjectId

interface ClipDao {

    fun getMaxClipId(): Int

    fun createClipData(clipData: ClipData)

    fun deleteClipData(id: ObjectId)

    fun getClipData(appInstanceId: String? = null,
                    limit: Int): RealmResults<ClipData>

    fun getClipData(appInstanceId: String?,
                    limit: Int,
                    createTime: RealmInstant,
                    excludeClipId: List<Int>): RealmResults<ClipData>
}