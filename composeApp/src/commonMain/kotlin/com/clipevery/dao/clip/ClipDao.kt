package com.clipevery.dao.clip

import com.clipevery.clip.ClipPlugin
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmInstant
import org.mongodb.kbson.ObjectId

interface ClipDao {

    fun getMaxClipId(): Int

    fun createClipData(clipData: ClipData): ObjectId

    suspend fun markDeleteClipData(id: ObjectId)

    fun deleteClipData(id: ObjectId)

    fun getClipData(appInstanceId: String? = null,
                    limit: Int): RealmResults<ClipData>

    fun getClipData(objectId: ObjectId): ClipData?

    suspend fun releaseClipData(id: ObjectId, clipPlugins: List<ClipPlugin>)

    fun updateClipItem(update: (MutableRealm) -> Unit)

    fun getClipDataGreaterThan(appInstanceId: String? = null,
                               createTime: RealmInstant): RealmResults<ClipData>

    fun getClipDataLessThan(appInstanceId: String? = null,
                            limit: Int,
                            createTime: RealmInstant): RealmResults<ClipData>
}