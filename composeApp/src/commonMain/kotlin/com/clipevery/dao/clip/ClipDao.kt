package com.clipevery.dao.clip

import com.clipevery.clip.ClipPlugin
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmInstant
import org.mongodb.kbson.ObjectId

interface ClipDao {

    fun getMaxClipId(): Long

    suspend fun createClipData(clipData: ClipData): ObjectId

    suspend fun markDeleteClipData(id: ObjectId)

    suspend fun deleteClipData(id: ObjectId)

    fun getClipResourceInfo(): ClipResourceInfo

    fun getClipData(appInstanceId: String? = null,
                    limit: Int): RealmResults<ClipData>

    fun getClipData(id: ObjectId): ClipData?

    fun getClipData(appInstanceId: String, clipId: Long): ClipData?

    suspend fun releaseLocalClipData(id: ObjectId, clipPlugins: List<ClipPlugin>)

    suspend fun releaseRemoteClipData(
        clipData: ClipData,
        tryWriteClipboard: (ClipData, Boolean) -> Unit
    )

    suspend fun releaseRemoteClipDataWithFile(
        id: ObjectId,
        tryWriteClipboard: (ClipData) -> Unit
    )

    fun update(update: (MutableRealm) -> Unit)

    suspend fun suspendUpdate(update: (MutableRealm) -> Unit)

    fun getClipDataLessThan(appInstanceId: String? = null,
                            limit: Int,
                            createTime: RealmInstant): RealmResults<ClipData>
}