package com.clipevery.dao.clip

import com.clipevery.clip.ClipPlugin
import com.clipevery.dao.task.TaskType
import com.clipevery.task.TaskExecutor
import com.clipevery.task.extra.SyncExtraInfo
import com.clipevery.utils.DateUtils
import com.clipevery.utils.TaskUtils.createTask
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import org.mongodb.kbson.ObjectId

class ClipRealm(
    private val realm: Realm,
    private val lazyTaskExecutor: Lazy<TaskExecutor>,
) : ClipDao {

    private val taskExecutor by lazy { lazyTaskExecutor.value }

    override fun getMaxClipId(): Long {
        return realm.query(ClipData::class).sort("clipId", Sort.DESCENDING).first().find()?.clipId ?: 0L
    }

    override fun setFavorite(
        id: ObjectId,
        isFavorite: Boolean,
    ) {
        realm.writeBlocking {
            query(ClipData::class, "id == $0", id).first().find()?.let {
                it.isFavorite = isFavorite
            }
        }
    }

    override suspend fun createClipData(clipData: ClipData): ObjectId {
        realm.write {
            copyToRealm(clipData)
        }
        return clipData.id
    }

    override suspend fun markDeleteClipData(id: ObjectId) {
        taskExecutor.submitTasks(
            realm.write {
                val markDeleteClipDatas = query(ClipData::class, "id == $0", id).first().find()?.let { listOf(it) } ?: emptyList()
                doMarkDeleteClipData(markDeleteClipDatas)
            },
        )
    }

    private fun MutableRealm.doMarkDeleteClipData(markDeleteClipDatas: List<ClipData>): List<ObjectId> {
        return markDeleteClipDatas.map {
            it.clipState = ClipState.DELETED
            copyToRealm(createTask(it.id, TaskType.DELETE_CLIP_TASK)).taskId
        }
    }

    private fun MutableRealm.markDeleteSameMd5(
        newClipDataId: ObjectId,
        newClipDataMd5: String,
    ): List<ObjectId> {
        val tasks = mutableListOf<ObjectId>()
        query(
            ClipData::class,
            "md5 == $0 AND createTime > $1 AND id != $2 AND clipState != $3",
            newClipDataMd5,
            DateUtils.getPrevDay(),
            newClipDataId,
            ClipState.DELETED,
        )
            .find().let { deleteClipDatas ->
                tasks.addAll(doMarkDeleteClipData(deleteClipDatas))
            }
        return tasks
    }

    override suspend fun deleteClipData(id: ObjectId) {
        doDeleteClipData {
            query(ClipData::class, "id == $0", id).first().find()?.let { listOf(it) } ?: emptyList()
        }
    }

    override fun getClipResourceInfo(): ClipResourceInfo {
        val number = realm.query(ClipData::class).count().find()
        var imagesSize: Long = 0
        var fileSize: Long = 0
        realm.query(ClipResource::class).find().forEach {
            imagesSize += it.imageSize
            fileSize += it.fileSize
        }
        return ClipResourceInfo(number, imagesSize, fileSize)
    }

    private suspend fun doDeleteClipData(queryToDelete: MutableRealm.() -> List<ClipData>) {
        realm.write {
            for (clipData in queryToDelete.invoke(this)) {
                clipData.clear(this)
            }
        }
    }

    override fun getClipData(
        appInstanceId: String?,
        limit: Int,
    ): RealmResults<ClipData> {
        val query =
            appInstanceId?.let {
                realm.query(ClipData::class, "appInstanceId == $0 AND clipState != $1", appInstanceId, ClipState.DELETED)
            } ?: realm.query(ClipData::class, "clipState != $0", ClipState.DELETED)
        return query.sort("createTime", Sort.DESCENDING).limit(limit).find()
    }

    override fun getClipData(id: ObjectId): ClipData? {
        return realm.query(ClipData::class, "id == $0 AND clipState != $1", id, ClipState.DELETED).first().find()
    }

    override fun getClipData(
        appInstanceId: String,
        clipId: Long,
    ): ClipData? {
        return realm.query(
            ClipData::class,
            "clipId == $0 AND appInstanceId == $1 AND clipState != $2",
            clipId,
            appInstanceId,
            ClipState.DELETED,
        ).first().find()
    }

    override suspend fun releaseLocalClipData(
        id: ObjectId,
        clipPlugins: List<ClipPlugin>,
    ) {
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
                    var clipAppearItems =
                        clipContent.clipAppearItems.mapNotNull { anyValue ->
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
                    tasks.add(copyToRealm(createTask(clipData.id, TaskType.SYNC_CLIP_TASK, SyncExtraInfo())).taskId)
                    tasks.addAll(markDeleteSameMd5(clipData.id, clipData.md5))
                    return@write tasks
                }
            }
        }?.let { tasks ->
            taskExecutor.submitTasks(tasks)
        }
    }

    override suspend fun releaseRemoteClipData(
        clipData: ClipData,
        tryWriteClipboard: (ClipData, Boolean) -> Unit,
    ) {
        val tasks = mutableListOf<ObjectId>()
        val existFile = clipData.existFileResource()

        realm.write(block = {
            query(ClipData::class, "id == $0 AND clipState != $1", clipData.id, ClipState.DELETED).first().find()?.let {
                return@write null
            }
            if (!existFile) {
                clipData.clipState = ClipState.LOADED
                copyToRealm(clipData)
                tasks.addAll(markDeleteSameMd5(clipData.id, clipData.md5))
            } else {
                val pullFileTask = createTask(clipData.id, TaskType.PULL_FILE_TASK)
                copyToRealm(clipData)
                copyToRealm(pullFileTask)
                tasks.add(pullFileTask.taskId)
            }
            return@write clipData
        })?.let {
            tryWriteClipboard(clipData, existFile)
            taskExecutor.submitTasks(tasks)
        }
    }

    override suspend fun releaseRemoteClipDataWithFile(
        id: ObjectId,
        tryWriteClipboard: (ClipData) -> Unit,
    ) {
        val tasks = mutableListOf<ObjectId>()
        realm.write {
            query(ClipData::class, "id == $0", id).first().find()?.let { clipData ->
                clipData.clipState = ClipState.LOADED
                tasks.addAll(markDeleteSameMd5(clipData.id, clipData.md5))
                return@write clipData
            }
        }?.let { clipData ->
            tryWriteClipboard(clipData)
        }
        taskExecutor.submitTasks(tasks)
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
        createTime: RealmInstant,
    ): RealmResults<ClipData> {
        val query =
            appInstanceId?.let {
                realm.query(
                    ClipData::class, "appInstanceId == $0 AND createTime <= $1 AND clipState != $2",
                    appInstanceId, createTime, ClipState.DELETED,
                )
            } ?: realm.query(ClipData::class, "createTime <= $0 AND clipState != $1", createTime, ClipState.DELETED)
        return query.sort("createTime", Sort.DESCENDING).limit(limit).find()
    }

    override suspend fun getMarkDeleteByCleanTime(
        cleanTime: RealmInstant,
        clipType: Int,
    ) {
        val taskIds =
            realm.write {
                doMarkDeleteClipData(
                    query(
                        ClipData::class,
                        "createTime < $0 AND clipType == $1 AND clipState != $2",
                        cleanTime,
                        clipType,
                        ClipState.DELETED,
                    ).find(),
                )
            }
        taskExecutor.submitTasks(taskIds)
    }
}
