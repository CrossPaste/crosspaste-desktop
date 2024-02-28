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

    override fun deleteClipData(id: ObjectId) {
        doDeleteClipData {
            query(ClipData::class, "id == $0", id).first().find()?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun doDeleteClipData(queryToDelete: MutableRealm.() -> List<ClipData>) {
        realm.writeBlocking {
            for (clipData in queryToDelete.invoke(this)) {
                try {
                    clipData.clear()
                } catch (e: Exception) {
                    logger.error(e) { "clear id ${clipData.id} fail" }
                }
                val clipAppearContent = clipData.clipAppearContent
                val clipAppearItem = ClipContent.getClipItem(clipAppearContent)
                val clipAppearItems = clipData.clipContent?.clipAppearItems?.mapNotNull { anyValue ->
                    ClipContent.getClipItem(anyValue)
                }
                delete(clipData)
                clipAppearItem?.let {
                    (clipAppearItem as? RealmObject)?.let {
                        delete(it)
                    }
                }
                clipAppearItems?.forEach { it ->
                    (it as? RealmObject)?.let {
                        delete(it)
                    }
                }
            }
        }
    }

    override fun getClipData(appInstanceId: String?, limit: Int): RealmResults<ClipData> {
        val query = appInstanceId?.let {
            realm.query(ClipData::class).query("appInstanceId == $0", appInstanceId)
        } ?: realm.query(ClipData::class)
        return query.sort("createTime", Sort.DESCENDING).limit(limit).find()
    }

    override fun getClipData(objectId: ObjectId): ClipData? {
        return realm.query(ClipData::class).query("id == $0", objectId).first().find()
    }

    override fun releaseClipData(id: ObjectId, clipPlugins: List<ClipPlugin>) {
        var md5: String? = null
        realm.writeBlocking {
            query(ClipData::class).query("id == $0", id).first().find()?.let { clipData ->
                clipData.clipContent?.let { clipContent ->
                    var clipAppearItems = clipContent.clipAppearItems.mapNotNull { anyValue ->
                        ClipContent.getClipItem(anyValue)
                    }

                    clipAppearItems = clipAppearItems.filter { it.md5 != "" }

                    assert(clipAppearItems.isNotEmpty())

                    for (clipPlugin in clipPlugins) {
                        clipAppearItems = clipPlugin.pluginProcess(clipAppearItems)
                    }

                    val firstItem: ClipAppearItem = clipAppearItems.first()

                    md5 = firstItem.md5

                    val remainingItems: List<ClipAppearItem> = clipAppearItems.drop(1)

                    val clipAppearContent: RealmAny = RealmAny.create(firstItem as RealmObject)

                    clipContent.clipAppearItems.clear()

                    clipContent.clipAppearItems.addAll(remainingItems.map { RealmAny.create(it as RealmObject) })

                    clipData.clipAppearContent = clipAppearContent
                    clipData.clipContent = clipContent
                    clipData.clipType = firstItem.getClipType()
                    clipData.clipSearchContent = firstItem.getSearchContent()
                    clipData.md5 = firstItem.md5
                    clipData.preCreate = false
                }
            }
        }

        md5?.let {
            doDeleteClipData {
                query(ClipData::class, "md5 == $0", it)
                    .query("createTime > $0", DateUtils.getPrevDay())
                    .query("id != $0", id)
                    .find().toList()
            }
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
        var query = appInstanceId?.let {
            realm.query(ClipData::class).query("appInstanceId == $0", appInstanceId)
        } ?: realm.query(ClipData::class)
        query = query.query("createTime >= $0", createTime)
        return query.sort("createTime", Sort.DESCENDING).find()
    }

    override fun getClipDataLessThan(
        appInstanceId: String?,
        limit: Int,
        createTime: RealmInstant
    ): RealmResults<ClipData> {
        var query = appInstanceId?.let {
            realm.query(ClipData::class).query("appInstanceId == $0", appInstanceId)
        } ?: realm.query(ClipData::class)
        query = query.query("createTime <= $0", createTime)
        return query.sort("createTime", Sort.DESCENDING).limit(limit).find()
    }
}