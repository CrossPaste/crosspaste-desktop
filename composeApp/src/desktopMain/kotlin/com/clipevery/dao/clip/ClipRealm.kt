package com.clipevery.dao.clip

import com.clipevery.app.AppFileType
import com.clipevery.clip.ClipPlugin
import com.clipevery.clip.item.FilesClipItem
import com.clipevery.clip.item.HtmlClipItem
import com.clipevery.clip.item.ImagesClipItem
import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.task.TaskType
import com.clipevery.path.PathProvider
import com.clipevery.task.TaskExecutor
import com.clipevery.task.extra.SyncExtraInfo
import com.clipevery.utils.LoggerExtension.logExecutionTime
import com.clipevery.utils.LoggerExtension.logSuspendExecutionTime
import com.clipevery.utils.TaskUtils.createTask
import com.clipevery.utils.getDateUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.query.min
import io.realm.kotlin.query.sum
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import org.mongodb.kbson.ObjectId
import kotlin.io.path.exists

class ClipRealm(
    private val realm: Realm,
    private val pathProvider: PathProvider,
    private val lazyTaskExecutor: Lazy<TaskExecutor>,
) : ClipDao {

    val logger = KotlinLogging.logger {}

    private val dateUtils = getDateUtils()

    private val taskExecutor by lazy { lazyTaskExecutor.value }

    override fun getMaxClipId(): Long {
        return realm.query(ClipData::class).sort("clipId", Sort.DESCENDING).first().find()?.clipId ?: 0L
    }

    override fun setFavorite(
        id: ObjectId,
        favorite: Boolean,
    ) {
        realm.writeBlocking {
            query(ClipData::class, "id == $0", id).first().find()?.let {
                it.favorite = favorite
                for (clipAppearItem in it.getClipAppearItems()) {
                    clipAppearItem.favorite = favorite
                }
            }
        }
    }

    override suspend fun createClipData(clipData: ClipData): ObjectId {
        return logSuspendExecutionTime(logger, "createClipData") {
            realm.write {
                copyToRealm(clipData)
            }
            return@logSuspendExecutionTime clipData.id
        }
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
            it.updateClipState(ClipState.DELETED)
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
            dateUtils.getPrevDay(),
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

    override fun getSize(allOrFavorite: Boolean): Long {
        return if (allOrFavorite) {
            realm.query(ClipData::class, "clipState = $0 AND clipState != $1", ClipState.LOADED, ClipState.DELETED)
                .sum<Long>("size").find()
        } else {
            realm.query(
                ClipData::class,
                "clipState = $0 AND favorite == $1 AND clipState != $2",
                ClipState.LOADED,
                true,
                ClipState.DELETED,
            ).sum<Long>("size").find()
        }
    }

    override fun getClipResourceInfo(allOrFavorite: Boolean): ClipResourceInfo {
        return if (allOrFavorite) {
            getAllClipResourceInfo()
        } else {
            getFavoriteClipResourceInfo()
        }
    }

    override fun getSizeByTimeLessThan(time: RealmInstant): Long {
        return realm.query(ClipData::class, "createTime < $0 AND clipState != $1", time, ClipState.DELETED)
            .sum<Long>("size").find()
    }

    override fun getMinClipDataCreateTime(): RealmInstant? {
        return realm.query(ClipData::class).min<RealmInstant>("createTime").find()
    }

    private fun getAllClipResourceInfo(): ClipResourceInfo {
        val query = realm.query(ClipData::class, "clipState != $0", ClipState.DELETED)
        val size = query.sum<Long>("size").find()

        val textQuery = realm.query(TextClipItem::class, "clipState != $0", ClipState.DELETED)
        val textCount = textQuery.count().find()
        val textSize = textQuery.sum<Long>("size").find()

        val urlQuery = realm.query(UrlClipItem::class, "clipState != $0", ClipState.DELETED)
        val urlCount = urlQuery.count().find()
        val urlSize = urlQuery.sum<Long>("size").find()

        val htmlQuery = realm.query(HtmlClipItem::class, "clipState != $0", ClipState.DELETED)
        val htmlCount = htmlQuery.count().find()
        val htmlSize = htmlQuery.sum<Long>("size").find()

        val imageQuery = realm.query(ImagesClipItem::class, "clipState != $0", ClipState.DELETED)
        val imageCount = imageQuery.sum<Long>("count").find()
        val imageSize = imageQuery.sum<Long>("size").find()

        val fileQuery = realm.query(FilesClipItem::class, "clipState != $0", ClipState.DELETED)
        val fileCount = fileQuery.sum<Long>("count").find()
        val fileSize = fileQuery.sum<Long>("size").find()

        val count = textCount + urlCount + htmlCount + imageCount + fileCount

        return ClipResourceInfo(
            count, size,
            textCount, textSize,
            urlCount, urlSize,
            htmlCount, htmlSize,
            imageCount, imageSize,
            fileCount, fileSize,
        )
    }

    private fun getFavoriteClipResourceInfo(): ClipResourceInfo {
        val query = realm.query(ClipData::class, "favorite == $0 AND clipState != $1", true, ClipState.DELETED)
        val size = query.sum<Long>("size").find()

        val textQuery = realm.query(TextClipItem::class, "favorite == $0 AND clipState != $1", true, ClipState.DELETED)
        val textCount = textQuery.count().find()
        val textSize = textQuery.sum<Long>("size").find()

        val urlQuery = realm.query(UrlClipItem::class, "favorite == $0 AND clipState != $1", true, ClipState.DELETED)
        val urlCount = urlQuery.count().find()
        val urlSize = urlQuery.sum<Long>("size").find()

        val htmlQuery = realm.query(HtmlClipItem::class, "favorite == $0 AND clipState != $1", true, ClipState.DELETED)
        val htmlCount = htmlQuery.count().find()
        val htmlSize = htmlQuery.sum<Long>("size").find()

        val imageQuery = realm.query(ImagesClipItem::class, "favorite == $0 AND clipState != $1", true, ClipState.DELETED)
        val imageCount = imageQuery.sum<Long>("count").find()
        val imageSize = imageQuery.sum<Long>("size").find()

        val fileQuery = realm.query(FilesClipItem::class, "favorite == $0 AND clipState != $1", true, ClipState.DELETED)
        val fileCount = fileQuery.sum<Long>("count").find()
        val fileSize = fileQuery.sum<Long>("size").find()

        val count = textCount + urlCount + htmlCount + imageCount + fileCount

        return ClipResourceInfo(
            count, size,
            textCount, textSize,
            urlCount, urlSize,
            htmlCount, htmlSize,
            imageCount, imageSize,
            fileCount, fileSize,
        )
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

                    val size = clipAppearItems.sumOf { it.size }
                    for (clipAppearItem in clipAppearItems) {
                        clipAppearItem.clipState = ClipState.LOADED
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
                    clipData.clipSearchContent = getSearchContent(firstItem, remainingItems)
                    clipData.md5 = firstItem.md5
                    clipData.size = size
                    clipData.clipState = ClipState.LOADED

                    val tasks = mutableListOf<ObjectId>()
                    if (clipData.clipType == ClipType.HTML) {
                        tasks.add(copyToRealm(createTask(clipData.id, TaskType.HTML_TO_IMAGE_TASK)).taskId)
                    }
                    tasks.add(copyToRealm(createTask(clipData.id, TaskType.SYNC_CLIP_TASK, SyncExtraInfo())).taskId)
                    tasks.addAll(markDeleteSameMd5(clipData.id, clipData.md5))
                    return@write tasks
                }
            }
        }?.let { tasks ->
            taskExecutor.submitTasks(tasks)
        }
    }

    private fun getSearchContent(
        firstItem: ClipAppearItem,
        remainingItems: List<ClipAppearItem>,
    ): String? {
        if (firstItem.getClipType() == ClipType.HTML) {
            remainingItems.firstOrNull { it.getClipType() == ClipType.TEXT }?.let {
                return@let it.getSearchContent()
            }
        }
        return firstItem.getSearchContent()
    }

    override suspend fun releaseRemoteClipData(
        clipData: ClipData,
        tryWriteClipboard: (ClipData, Boolean) -> Unit,
    ) {
        val tasks = mutableListOf<ObjectId>()
        val existFile = clipData.existFileResource()
        val existIconFile: Boolean? =
            clipData.source?.let {
                pathProvider.resolve("$it.png", AppFileType.ICON).exists()
            }

        realm.write(block = {
            query(ClipData::class, "id == $0 AND clipState != $1", clipData.id, ClipState.DELETED).first().find()?.let {
                return@write null
            }
            if (!existFile) {
                clipData.updateClipState(ClipState.LOADED)
                copyToRealm(clipData)
                tasks.addAll(markDeleteSameMd5(clipData.id, clipData.md5))
                if (clipData.clipType == ClipType.HTML) {
                    tasks.add(copyToRealm(createTask(clipData.id, TaskType.HTML_TO_IMAGE_TASK)).taskId)
                }
            } else {
                val pullFileTask = createTask(clipData.id, TaskType.PULL_FILE_TASK)
                copyToRealm(clipData)
                copyToRealm(pullFileTask)
                tasks.add(pullFileTask.taskId)
            }

            existIconFile?.let {
                if (!it) {
                    val pullIconTask = createTask(clipData.id, TaskType.PULL_ICON_TASK)
                    copyToRealm(pullIconTask)
                    tasks.add(pullIconTask.taskId)
                }
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
                clipData.updateClipState(ClipState.LOADED)
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

    override suspend fun markDeleteByCleanTime(
        cleanTime: RealmInstant,
        clipType: Int?,
    ) {
        val taskIds =
            realm.write {
                var query =
                    query(
                        ClipData::class,
                        "createTime < $0 AND clipState != $1",
                        cleanTime,
                        ClipState.DELETED,
                    )

                clipType?.let {
                    query = query.query("clipType == $0", it)
                }

                doMarkDeleteClipData(query.find())
            }
        taskExecutor.submitTasks(taskIds)
    }

    override fun searchClipData(
        searchTerms: List<String>,
        favorite: Boolean?,
        appInstanceId: String?,
        clipType: Int?,
        sort: Boolean,
        limit: Int,
    ): RealmResults<ClipData> {
        return logExecutionTime(logger, "searchClipData") {
            logger.info { "Performing search for: $searchTerms" }
            var query = realm.query(ClipData::class, "clipState != $0", ClipState.DELETED)

            if (searchTerms.isNotEmpty()) {
                query = query.query("clipSearchContent LIKE $0", "*${searchTerms[0]}*")
                for (i in 1 until searchTerms.size) {
                    query = query.query("clipSearchContent LIKE $0", "*${searchTerms[i]}*")
                }
            }

            if (favorite != null) {
                query = query.query("favorite == $0", favorite)
            }
            if (appInstanceId != null) {
                query = query.query("appInstanceId == $0", appInstanceId)
            }
            if (clipType != null) {
                query = query.query("clipType == $0", clipType)
            }

            return@logExecutionTime query.sort("createTime", if (sort) Sort.DESCENDING else Sort.ASCENDING).limit(limit).find()
        }
    }
}
