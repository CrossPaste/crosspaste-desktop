package com.clipevery.dao.clip

import com.clipevery.clip.ClipPlugin
import com.clipevery.utils.DateUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import org.mongodb.kbson.ObjectId

class ClipRealm(private val realm: Realm) : ClipDao {

    private val logger = KotlinLogging.logger {}

    override fun getMaxClipId(): Int {
        return realm.query(ClipData::class).sort("clipId", Sort.DESCENDING).first().find()?.clipId ?: 0
    }

    override fun createClipData(clipData: ClipData): ObjectId {
        realm.writeBlocking {
            copyToRealm(clipData)
        }
        return clipData.id
    }

    override fun markDeleteClipData(id: ObjectId) {
        doMarkDeleteClipData {
            query(ClipData::class, "id == $0", id).first().find()?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun doMarkDeleteClipData(queryToMarkDelete: MutableRealm.() -> List<ClipData>) {
        realm.writeBlocking {
            for (clipData in queryToMarkDelete.invoke(this)) {
                clipData.clipState = ClipState.DELETED
            }
        }
    }

    override fun deleteClipData(id: ObjectId) {
        doDeleteClipData {
            query(ClipData::class, "id == $0", id).first().find()?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun doDeleteClipData(queryToDelete: MutableRealm.() -> List<ClipData>) {
        realm.writeBlocking {
            for (clipData in queryToDelete.invoke(this)) {
                try {
                    clipData.clear(this)
                } catch (e: Exception) {
                    logger.error(e) { "clear id ${clipData.id} fail" }
                }
            }
        }
    }

    override fun getClipData(appInstanceId: String?, limit: Int): RealmResults<ClipData> {
        val query = appInstanceId?.let {
            realm.query(ClipData::class, "appInstanceId == $0 AND clipState != $1", appInstanceId, ClipState.DELETED)
        } ?: realm.query(ClipData::class)
        return query.sort("createTime", Sort.DESCENDING).limit(limit).find()
    }

    override fun getClipData(objectId: ObjectId): ClipData? {
        return realm.query(ClipData::class, "id == $0  AND clipState != $1", objectId, ClipState.DELETED).first().find()
    }

    @Synchronized
    override fun releaseClipData(id: ObjectId, clipPlugins: List<ClipPlugin>) {
        var md5: String? = null
        var releaseFail = false
        realm.writeBlocking {
            query(ClipData::class, "id == $0 AND clipState == $1", id, ClipState.LOADING).first().find()?.let { clipData ->
                clipData.clipContent?.let { clipContent ->
                    try {
                        val iterator = clipContent.clipAppearItems.iterator()

                        while (iterator.hasNext()) {
                            val anyValue = iterator.next()
                            ClipContent.getClipItem(anyValue)?.let {
                                if (it.md5 == "") {
                                    iterator.remove()
                                    it.clear(this)
                                }
                            } ?: iterator.remove()
                        }

                        var clipAppearItems = clipContent.clipAppearItems.mapNotNull { anyValue ->
                            ClipContent.getClipItem(anyValue)
                        }

                        assert(clipAppearItems.isNotEmpty())

                        clipContent.clipAppearItems.clear()

                        for (clipPlugin in clipPlugins) {
                            clipAppearItems = clipPlugin.pluginProcess(clipAppearItems, this)
                        }

                        val firstItem: ClipAppearItem = clipAppearItems.first()

                        md5 = firstItem.md5

                        val remainingItems: List<ClipAppearItem> = clipAppearItems.drop(1)

                        val clipAppearContent: RealmAny = RealmAny.create(firstItem as RealmObject)

                        clipContent.clipAppearItems.addAll(remainingItems.map { RealmAny.create(it as RealmObject) })

                        clipData.clipAppearContent = clipAppearContent
                        clipData.clipContent = clipContent
                        clipData.clipType = firstItem.getClipType()
                        clipData.clipSearchContent = firstItem.getSearchContent()
                        clipData.md5 = firstItem.md5
                        clipData.clipState = ClipState.LOADED
                    } catch (e: Exception) {
                        logger.error(e) { "releaseClipData fail" }
                        releaseFail = true
                    }
                }
            }
        }

        if (!releaseFail) {
            md5?.let {
                doMarkDeleteClipData {
                    query(ClipData::class, "md5 == $0 AND createTime > $1 AND id != $2 AND clipState != $3"
                        , it, DateUtils.getPrevDay(), id, ClipState.DELETED)
                        .find().toList()
                }
            }
        } else {
            markDeleteClipData(id)
        }
    }

    override fun updateClipItem(update: (MutableRealm) -> Unit) {
        realm.writeBlocking {
            update(this)
        }
    }

    override fun getClipDataGreaterThan(
        appInstanceId: String?,
        createTime: RealmInstant
    ): RealmResults<ClipData> {
        val query = appInstanceId?.let {
            realm.query(ClipData::class, "appInstanceId == $0 AND createTime >= $1 AND clipState != $2",
                appInstanceId, createTime, ClipState.DELETED)
        } ?: realm.query(ClipData::class, "createTime >= $0 AND clipState != $1", createTime, ClipState.DELETED)
        return query.sort("createTime", Sort.DESCENDING).find()
    }

    override fun getClipDataLessThan(
        appInstanceId: String?,
        limit: Int,
        createTime: RealmInstant
    ): RealmResults<ClipData> {
        val query = appInstanceId?.let {
            realm.query(ClipData::class, "appInstanceId == $0 AND createTime <= $1 AND clipState != $2",
                appInstanceId, createTime, ClipState.DELETED)
        } ?: realm.query(ClipData::class, "createTime <= $0 AND clipState != $1", createTime, ClipState.DELETED)
        return query.sort("createTime", Sort.DESCENDING).limit(limit).find()
    }
}