package com.clipevery.dao.clip

import com.clipevery.clip.ClipPlugin
import com.clipevery.dao.task.TaskType
import com.clipevery.task.TaskExecutor
import com.clipevery.task.extra.SyncExtraInfo
import com.clipevery.utils.DateUtils
import com.clipevery.utils.TaskUtils.createTask
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import org.mongodb.kbson.ObjectId

class ClipRealm(private val realm: Realm,
                private val taskExecutor: TaskExecutor) : ClipDao {

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

    override suspend fun markDeleteClipData(id: ObjectId) {
        taskExecutor.submitTasks(realm.write {
            val markDeleteClipDatas = query(ClipData::class, "id == $0", id).first().find()?.let { listOf(it) } ?: emptyList()
            doMarkDeleteClipData(markDeleteClipDatas)
        })
    }

    private fun MutableRealm.doMarkDeleteClipData(markDeleteClipDatas: List<ClipData>): List<ObjectId> {
        return markDeleteClipDatas.map {
            it.clipState = ClipState.DELETED
            copyToRealm(createTask(it.clipId, TaskType.DELETE_CLIP_TASK)).taskId
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
        } ?: realm.query(ClipData::class, "clipState != $0", ClipState.DELETED)
        return query.sort("createTime", Sort.DESCENDING).limit(limit).find()
    }

    override fun getClipData(objectId: ObjectId): ClipData? {
        return realm.query(ClipData::class, "id == $0 AND clipState != $1", objectId, ClipState.DELETED).first().find()
    }

    override fun getClipData(clipId: Int): ClipData? {
        return realm.query(ClipData::class, "clipId == $0 AND clipState != $1", clipId, ClipState.DELETED).first().find()
    }

    override suspend fun releaseLocalClipData(id: ObjectId, clipPlugins: List<ClipPlugin>) {
        realm.write {
            query(ClipData::class, "id == $0 AND clipState == $1", id, ClipState.LOADING).first().find()?.let { clipData ->
                clipData.clipContent?.let { clipContent ->
                    // remove not update appearItems
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

                    // use plugin to process clipAppearItems
                    var clipAppearItems = clipContent.clipAppearItems.mapNotNull { anyValue ->
                        ClipContent.getClipItem(anyValue)
                    }
                    assert(clipAppearItems.isNotEmpty())
                    clipContent.clipAppearItems.clear()
                    for (clipPlugin in clipPlugins) {
                        clipAppearItems = clipPlugin.pluginProcess(clipAppearItems, this)
                    }

                    // first appearItem as clipAppearContent
                    // remaining appearItems as clipContent
                    val firstItem: ClipAppearItem = clipAppearItems.first()
                    val remainingItems: List<ClipAppearItem> = clipAppearItems.drop(1)
                    val clipAppearContent: RealmAny = RealmAny.create(firstItem as RealmObject)

                    // update realm data
                    clipData.clipAppearContent = clipAppearContent
                    clipContent.clipAppearItems.addAll(remainingItems.map { RealmAny.create(it as RealmObject) })
                    clipData.clipType = firstItem.getClipType()
                    clipData.clipSearchContent = firstItem.getSearchContent()
                    clipData.md5 = firstItem.md5
                    clipData.clipState = ClipState.LOADED

                    val tasks = mutableListOf<ObjectId>()
                    tasks.add(copyToRealm(createTask(clipData.clipId, TaskType.SYNC_CLIP_TASK, SyncExtraInfo())).taskId)
                    query(ClipData::class, "md5 == $0 AND createTime > $1 AND id != $2 AND clipState != $3",
                        clipData.md5, DateUtils.getPrevDay(), id, ClipState.DELETED)
                        .find().let { deleteClipDatas ->
                            tasks.addAll(doMarkDeleteClipData(deleteClipDatas))
                        }
                    return@write tasks
                }
            }
        }?.let { tasks ->
            taskExecutor.submitTasks(tasks)
        }
    }

    override suspend fun releaseRemoteClipData(id: ObjectId) {
        realm.write {
            query(ClipData::class, "id == $0 AND clipState == $1", id, ClipState.LOADING).first().find()?.let { clipData ->
                clipData.clipState = ClipState.LOADED
            }
        }
    }

    override fun update(update: (MutableRealm) -> Unit) {
        realm.writeBlocking {
            update(this)
        }
    }

    override suspend fun suspendUpdate(update: (MutableRealm) -> Unit) {
        realm.write {
            update(this)
        }
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