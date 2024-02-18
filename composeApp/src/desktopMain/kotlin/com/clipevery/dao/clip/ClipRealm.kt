package com.clipevery.dao.clip

import com.clipevery.utils.DateUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmObject
import org.mongodb.kbson.ObjectId

class ClipRealm(private val realm: Realm): ClipDao {

    private val logger = KotlinLogging.logger {}

    override fun getMaxClipId(): Int {
        return realm.query(ClipData::class).sort("clipId", Sort.DESCENDING).first().find()?.clipId ?: 0
    }

    override fun createClipData(clipData: ClipData) {
        realm.writeBlocking {
            copyToRealm(clipData)
        }
        doDeleteClipData {
            query(ClipData::class, "md5 == $0", clipData.md5)
                .query("createTime > $0", DateUtils.getPrevDay()).find().toList()
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
                    clipData.clear()
                } catch (e: Exception) {
                    logger.error(e) { "clear id ${clipData.id} fail" }
                }
                val clipAppearContent = clipData.clipAppearContent
                val clipAppearItem = ClipContent.getClipItem(clipAppearContent)
                val clipAppearItems = clipData.clipContent?.clipAppearItems?.mapNotNull{ anyValue ->
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
}