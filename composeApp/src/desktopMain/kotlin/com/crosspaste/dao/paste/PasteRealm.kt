package com.crosspaste.dao.paste

import com.crosspaste.app.AppFileType
import com.crosspaste.dao.task.TaskType
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.path.PathProvider
import com.crosspaste.task.TaskExecutor
import com.crosspaste.task.extra.SyncExtraInfo
import com.crosspaste.utils.LoggerExtension.logExecutionTime
import com.crosspaste.utils.LoggerExtension.logSuspendExecutionTime
import com.crosspaste.utils.TaskUtils.createTask
import com.crosspaste.utils.getDateUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.query.min
import io.realm.kotlin.query.sum
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import okio.FileSystem
import org.mongodb.kbson.ObjectId

class PasteRealm(
    private val realm: Realm,
    private val pathProvider: PathProvider,
    private val lazyTaskExecutor: Lazy<TaskExecutor>,
) : PasteDao {

    val logger = KotlinLogging.logger {}

    private val dateUtils = getDateUtils()

    private val taskExecutor by lazy { lazyTaskExecutor.value }

    override fun getMaxPasteId(): Long {
        return realm.query(PasteData::class).sort("pasteId", Sort.DESCENDING).first().find()?.pasteId ?: 0L
    }

    override fun setFavorite(
        id: ObjectId,
        favorite: Boolean,
    ) {
        realm.writeBlocking {
            query(PasteData::class, "id == $0", id).first().find()?.let {
                it.favorite = favorite
                for (pasteAppearItem in it.getPasteAppearItems()) {
                    pasteAppearItem.favorite = favorite
                }
            }
        }
    }

    override suspend fun createPasteData(pasteData: PasteData): ObjectId {
        return logSuspendExecutionTime(logger, "createPasteData") {
            realm.write {
                copyToRealm(pasteData)
            }
            return@logSuspendExecutionTime pasteData.id
        }
    }

    override suspend fun markDeletePasteData(id: ObjectId) {
        taskExecutor.submitTasks(
            realm.write {
                val markDeletePasteData = query(PasteData::class, "id == $0", id).first().find()?.let { listOf(it) } ?: emptyList()
                doMarkDeletePasteData(markDeletePasteData)
            },
        )
    }

    private fun MutableRealm.doMarkDeletePasteData(markDeletePasteData: List<PasteData>): List<ObjectId> {
        return markDeletePasteData.map {
            it.updatePasteState(PasteState.DELETED)
            copyToRealm(createTask(it.id, TaskType.DELETE_PASTE_TASK)).taskId
        }
    }

    private fun MutableRealm.markDeleteSameMd5(
        newPasteDataId: ObjectId,
        newPasteDataMd5: String,
    ): List<ObjectId> {
        val tasks = mutableListOf<ObjectId>()
        query(
            PasteData::class,
            "md5 == $0 AND createTime > $1 AND id != $2 AND pasteState != $3",
            newPasteDataMd5,
            dateUtils.getPrevDay(),
            newPasteDataId,
            PasteState.DELETED,
        )
            .find().let { deletePasteDatas ->
                tasks.addAll(doMarkDeletePasteData(deletePasteDatas))
            }
        return tasks
    }

    override suspend fun deletePasteData(id: ObjectId) {
        doDeletePasteData {
            query(PasteData::class, "id == $0", id).first().find()?.let { listOf(it) } ?: emptyList()
        }
    }

    override fun getSize(allOrFavorite: Boolean): Long {
        return if (allOrFavorite) {
            realm.query(PasteData::class, "pasteState = $0 AND pasteState != $1", PasteState.LOADED, PasteState.DELETED)
                .sum<Long>("size").find()
        } else {
            realm.query(
                PasteData::class,
                "pasteState = $0 AND favorite == $1 AND pasteState != $2",
                PasteState.LOADED,
                true,
                PasteState.DELETED,
            ).sum<Long>("size").find()
        }
    }

    override fun getPasteResourceInfo(allOrFavorite: Boolean): PasteResourceInfo {
        return if (allOrFavorite) {
            getAllPasteResourceInfo()
        } else {
            getFavoritePasteResourceInfo()
        }
    }

    override fun getSizeByTimeLessThan(time: RealmInstant): Long {
        return realm.query(PasteData::class, "createTime < $0 AND pasteState != $1", time, PasteState.DELETED)
            .sum<Long>("size").find()
    }

    override fun getMinPasteDataCreateTime(): RealmInstant? {
        return realm.query(PasteData::class).min<RealmInstant>("createTime").find()
    }

    private fun getAllPasteResourceInfo(): PasteResourceInfo {
        val query = realm.query(PasteData::class, "pasteState != $0", PasteState.DELETED)
        val size = query.sum<Long>("size").find()

        val textQuery = realm.query(TextPasteItem::class, "pasteState != $0", PasteState.DELETED)
        val textCount = textQuery.count().find()
        val textSize = textQuery.sum<Long>("size").find()

        val urlQuery = realm.query(UrlPasteItem::class, "pasteState != $0", PasteState.DELETED)
        val urlCount = urlQuery.count().find()
        val urlSize = urlQuery.sum<Long>("size").find()

        val htmlQuery = realm.query(HtmlPasteItem::class, "pasteState != $0", PasteState.DELETED)
        val htmlCount = htmlQuery.count().find()
        val htmlSize = htmlQuery.sum<Long>("size").find()

        val imageQuery = realm.query(ImagesPasteItem::class, "pasteState != $0", PasteState.DELETED)
        val imageCount = imageQuery.sum<Long>("count").find()
        val imageSize = imageQuery.sum<Long>("size").find()

        val fileQuery = realm.query(FilesPasteItem::class, "pasteState != $0", PasteState.DELETED)
        val fileCount = fileQuery.sum<Long>("count").find()
        val fileSize = fileQuery.sum<Long>("size").find()

        val count = textCount + urlCount + htmlCount + imageCount + fileCount

        return PasteResourceInfo(
            count, size,
            textCount, textSize,
            urlCount, urlSize,
            htmlCount, htmlSize,
            imageCount, imageSize,
            fileCount, fileSize,
        )
    }

    private fun getFavoritePasteResourceInfo(): PasteResourceInfo {
        val query = realm.query(PasteData::class, "favorite == $0 AND pasteState != $1", true, PasteState.DELETED)
        val size = query.sum<Long>("size").find()

        val textQuery = realm.query(TextPasteItem::class, "favorite == $0 AND pasteState != $1", true, PasteState.DELETED)
        val textCount = textQuery.count().find()
        val textSize = textQuery.sum<Long>("size").find()

        val urlQuery = realm.query(UrlPasteItem::class, "favorite == $0 AND pasteState != $1", true, PasteState.DELETED)
        val urlCount = urlQuery.count().find()
        val urlSize = urlQuery.sum<Long>("size").find()

        val htmlQuery = realm.query(HtmlPasteItem::class, "favorite == $0 AND pasteState != $1", true, PasteState.DELETED)
        val htmlCount = htmlQuery.count().find()
        val htmlSize = htmlQuery.sum<Long>("size").find()

        val imageQuery = realm.query(ImagesPasteItem::class, "favorite == $0 AND pasteState != $1", true, PasteState.DELETED)
        val imageCount = imageQuery.sum<Long>("count").find()
        val imageSize = imageQuery.sum<Long>("size").find()

        val fileQuery = realm.query(FilesPasteItem::class, "favorite == $0 AND pasteState != $1", true, PasteState.DELETED)
        val fileCount = fileQuery.sum<Long>("count").find()
        val fileSize = fileQuery.sum<Long>("size").find()

        val count = textCount + urlCount + htmlCount + imageCount + fileCount

        return PasteResourceInfo(
            count, size,
            textCount, textSize,
            urlCount, urlSize,
            htmlCount, htmlSize,
            imageCount, imageSize,
            fileCount, fileSize,
        )
    }

    private suspend fun doDeletePasteData(queryToDelete: MutableRealm.() -> List<PasteData>) {
        realm.write {
            for (pasteData in queryToDelete.invoke(this)) {
                pasteData.clear(this)
            }
        }
    }

    override fun getPasteData(
        appInstanceId: String?,
        limit: Int,
    ): RealmResults<PasteData> {
        val query =
            appInstanceId?.let {
                realm.query(PasteData::class, "appInstanceId == $0 AND pasteState != $1", appInstanceId, PasteState.DELETED)
            } ?: realm.query(PasteData::class, "pasteState != $0", PasteState.DELETED)
        return query.sort("createTime", Sort.DESCENDING).limit(limit).find()
    }

    override fun getPasteData(id: ObjectId): PasteData? {
        return realm.query(PasteData::class, "id == $0 AND pasteState != $1", id, PasteState.DELETED).first().find()
    }

    override fun getPasteData(
        appInstanceId: String,
        pasteId: Long,
    ): PasteData? {
        return realm.query(
            PasteData::class,
            "pasteId == $0 AND appInstanceId == $1 AND pasteState != $2",
            pasteId,
            appInstanceId,
            PasteState.DELETED,
        ).first().find()
    }

    override suspend fun releaseLocalPasteData(
        id: ObjectId,
        pasteProcessPlugins: List<PasteProcessPlugin>,
    ) {
        realm.write {
            query(PasteData::class, "id == $0 AND pasteState == $1", id, PasteState.LOADING).first().find()?.let { pasteData ->
                pasteData.pasteCollection?.let { pasteCollection ->
                    // remove not update pasteItems
                    val iterator = pasteCollection.pasteItems.iterator()
                    while (iterator.hasNext()) {
                        val anyValue = iterator.next()
                        PasteCollection.getPasteItem(anyValue)?.let {
                            if (it.md5 == "") {
                                iterator.remove()
                                it.clear(this)
                            }
                        } ?: iterator.remove()
                    }

                    // use plugin to process pasteItems
                    var pasteAppearItems =
                        pasteCollection.pasteItems.mapNotNull { anyValue ->
                            PasteCollection.getPasteItem(anyValue)
                        }
                    assert(pasteAppearItems.isNotEmpty())
                    pasteCollection.pasteItems.clear()
                    for (pastePlugin in pasteProcessPlugins) {
                        pasteAppearItems = pastePlugin.process(pasteAppearItems, this)
                    }

                    val size = pasteAppearItems.sumOf { it.size }
                    for (pasteAppearItem in pasteAppearItems) {
                        pasteAppearItem.pasteState = PasteState.LOADED
                    }

                    // first item as pasteAppearItem
                    // remaining items as pasteContent
                    val firstItem: PasteItem = pasteAppearItems.first()
                    val remainingItems: List<PasteItem> = pasteAppearItems.drop(1)
                    val pasteAppearItem: RealmAny = RealmAny.create(firstItem as RealmObject)

                    // update realm data
                    pasteData.pasteAppearItem = pasteAppearItem
                    pasteCollection.pasteItems.addAll(remainingItems.map { RealmAny.create(it as RealmObject) })
                    pasteData.pasteType = firstItem.getPasteType()
                    pasteData.pasteSearchContent =
                        PasteData.createSearchContent(
                            pasteData.source,
                            getSearchContent(firstItem, remainingItems),
                        )
                    pasteData.md5 = firstItem.md5
                    pasteData.size = size
                    pasteData.pasteState = PasteState.LOADED

                    val tasks = mutableListOf<ObjectId>()
                    if (pasteData.pasteType == PasteType.HTML) {
                        tasks.add(copyToRealm(createTask(pasteData.id, TaskType.HTML_TO_IMAGE_TASK)).taskId)
                    }
                    tasks.add(copyToRealm(createTask(pasteData.id, TaskType.SYNC_PASTE_TASK, SyncExtraInfo())).taskId)
                    tasks.addAll(markDeleteSameMd5(pasteData.id, pasteData.md5))
                    return@write tasks
                }
            }
        }?.let { tasks ->
            taskExecutor.submitTasks(tasks)
        }
    }

    private fun getSearchContent(
        firstItem: PasteItem,
        remainingItems: List<PasteItem>,
    ): String? {
        if (firstItem.getPasteType() == PasteType.HTML) {
            remainingItems.firstOrNull { it.getPasteType() == PasteType.TEXT }?.let {
                return@let it.getSearchContent()
            }
        }
        return firstItem.getSearchContent()
    }

    override suspend fun releaseRemotePasteData(
        pasteData: PasteData,
        tryWritePasteboard: (PasteData, Boolean) -> Unit,
    ) {
        val tasks = mutableListOf<ObjectId>()
        val existFile = pasteData.existFileResource()
        val existIconFile: Boolean? =
            pasteData.source?.let {
                FileSystem.SYSTEM.exists(pathProvider.resolve("$it.png", AppFileType.ICON))
            }

        realm.write(block = {
            query(PasteData::class, "id == $0 AND pasteState != $1", pasteData.id, PasteState.DELETED)
                .first().find()?.let {
                    return@write null
                }
            if (!existFile) {
                pasteData.updatePasteState(PasteState.LOADED)
                copyToRealm(pasteData)
                tasks.addAll(markDeleteSameMd5(pasteData.id, pasteData.md5))
                if (pasteData.pasteType == PasteType.HTML) {
                    tasks.add(copyToRealm(createTask(pasteData.id, TaskType.HTML_TO_IMAGE_TASK)).taskId)
                }
            } else {
                val pullFileTask = createTask(pasteData.id, TaskType.PULL_FILE_TASK)
                pasteData.adaptRelativePaths(pasteData.appInstanceId, pasteData.pasteId)
                copyToRealm(pasteData)
                copyToRealm(pullFileTask)
                tasks.add(pullFileTask.taskId)
            }

            existIconFile?.let {
                if (!it) {
                    val pullIconTask = createTask(pasteData.id, TaskType.PULL_ICON_TASK)
                    copyToRealm(pullIconTask)
                    tasks.add(pullIconTask.taskId)
                }
            }

            return@write pasteData
        })?.let {
            tryWritePasteboard(pasteData, existFile)
            taskExecutor.submitTasks(tasks)
        }
    }

    override suspend fun releaseRemotePasteDataWithFile(
        id: ObjectId,
        tryWritePasteboard: (PasteData) -> Unit,
    ) {
        val tasks = mutableListOf<ObjectId>()
        realm.write {
            query(PasteData::class, "id == $0", id).first().find()?.let { pasteData ->
                pasteData.updatePasteState(PasteState.LOADED)
                tasks.addAll(markDeleteSameMd5(pasteData.id, pasteData.md5))
                return@write pasteData
            }
        }?.let { pasteData ->
            tryWritePasteboard(pasteData)
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

    override fun getPasteDataLessThan(
        appInstanceId: String?,
        limit: Int,
        createTime: RealmInstant,
    ): RealmResults<PasteData> {
        val query =
            appInstanceId?.let {
                realm.query(
                    PasteData::class, "appInstanceId == $0 AND createTime <= $1 AND pasteState != $2",
                    appInstanceId, createTime, PasteState.DELETED,
                )
            } ?: realm.query(PasteData::class, "createTime <= $0 AND pasteState != $1", createTime, PasteState.DELETED)
        return query.sort("createTime", Sort.DESCENDING).limit(limit).find()
    }

    override suspend fun markDeleteByCleanTime(
        cleanTime: RealmInstant,
        pasteType: Int?,
    ) {
        val taskIds =
            realm.write {
                var query =
                    query(
                        PasteData::class,
                        "createTime < $0 AND pasteState != $1",
                        cleanTime,
                        PasteState.DELETED,
                    )

                pasteType?.let {
                    query = query.query("pasteType == $0", it)
                }

                doMarkDeletePasteData(query.find())
            }
        taskExecutor.submitTasks(taskIds)
    }

    override fun searchPasteData(
        searchTerms: List<String>,
        favorite: Boolean?,
        appInstanceIdQuery: (RealmQuery<PasteData>) -> RealmQuery<PasteData>,
        pasteType: Int?,
        sort: Boolean,
        limit: Int,
    ): RealmResults<PasteData> {
        return logExecutionTime(logger, "searchPasteData") {
            logger.info { "Performing search for: $searchTerms" }
            var query = realm.query(PasteData::class, "pasteState != $0", PasteState.DELETED)

            if (searchTerms.isNotEmpty()) {
                query = query.query("pasteSearchContent LIKE $0", "*${searchTerms[0]}*")
                for (i in 1 until searchTerms.size) {
                    query = query.query("pasteSearchContent LIKE $0", "*${searchTerms[i]}*")
                }
            }

            query = appInstanceIdQuery(query)

            if (favorite != null) {
                query = query.query("favorite == $0", favorite)
            }

            if (pasteType != null) {
                query = query.query("pasteType == $0", pasteType)
            }

            return@logExecutionTime query.sort("createTime", if (sort) Sort.DESCENDING else Sort.ASCENDING).limit(limit).find()
        }
    }
}
