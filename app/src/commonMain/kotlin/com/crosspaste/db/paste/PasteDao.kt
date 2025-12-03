package com.crosspaste.db.paste

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import com.crosspaste.Database
import com.crosspaste.app.AppControl
import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.db.task.PullExtraInfo
import com.crosspaste.db.task.SyncExtraInfo
import com.crosspaste.db.task.TaskDao
import com.crosspaste.db.task.TaskType
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteExportParam
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteTag.Companion.getColor
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.task.TaskExecutor
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.LoggerExtension.logExecutionTime
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.ExperimentalTime

class PasteDao(
    private val appControl: AppControl,
    private val appInfo: AppInfo,
    private val currentPaste: CurrentPaste,
    private val database: Database,
    private val lazyTaskExecutor: Lazy<TaskExecutor>,
    private val pasteProcessPlugins: List<PasteProcessPlugin>,
    private val searchContentService: SearchContentService,
    private val taskDao: TaskDao,
    private val userDataPathProvider: UserDataPathProvider,
) {

    companion object {
        private val fileUtils = getFileUtils()
    }

    val logger = KotlinLogging.logger {}

    private val pasteDatabaseQueries = database.pasteDatabaseQueries

    private val tagDatabaseQueries = database.tagDatabaseQueries

    private val taskExecutor by lazy { lazyTaskExecutor.value }

    private val markDeleteBatchNum = 50L

    fun getNoDeletePasteData(id: Long): PasteData? {
        return pasteDatabaseQueries.getPasteData(
            id,
            listOf(PasteState.LOADING.toLong(), PasteState.LOADED.toLong()),
            PasteData::mapper,
        ).executeAsOneOrNull()
    }

    fun getLoadingPasteData(id: Long): PasteData? {
        return pasteDatabaseQueries.getPasteData(
            id,
            listOf(PasteState.LOADING.toLong()),
            PasteData::mapper,
        ).executeAsOneOrNull()
    }

    fun getLoadedPasteData(id: Long): PasteData? {
        return pasteDatabaseQueries.getPasteData(
            id,
            listOf(PasteState.LOADED.toLong()),
            PasteData::mapper,
        ).executeAsOneOrNull()
    }

    fun getDeletePasteData(id: Long): PasteData? {
        return pasteDatabaseQueries.getPasteData(
            id,
            listOf(PasteState.DELETED.toLong()),
            PasteData::mapper,
        ).executeAsOneOrNull()
    }

    fun setFavorite(
        pasteId: Long,
        favorite: Boolean,
    ) {
        pasteDatabaseQueries.updateFavorite(favorite, pasteId)
    }

    fun createPasteData(pasteData: PasteData, pasteState: Int? = null): Long {
        return database.transactionWithResult {
            pasteDatabaseQueries.createPasteDataEntity(
                pasteData.appInstanceId,
                pasteData.favorite,
                pasteData.pasteAppearItem?.toJson(),
                pasteData.pasteCollection.toJson(),
                pasteData.pasteType.toLong(),
                pasteData.source,
                pasteData.size,
                pasteData.hash,
                pasteData.createTime,
                searchContentService.createSearchContent(
                    pasteData.source,
                    pasteData.pasteAppearItem?.getSearchContent(),
                ),
                (pasteState ?: pasteData.pasteState).toLong(),
                pasteData.remote,
            )
            pasteDatabaseQueries.getLastId().executeAsOne()
        }
    }

    fun updateFilePath(pasteData: PasteData) {
        pasteDatabaseQueries.updateRemotePasteDataWithFile(
            pasteData.pasteAppearItem?.toJson(),
            pasteData.pasteCollection.toJson(),
            searchContentService.createSearchContent(
                pasteData.source,
                pasteData.pasteAppearItem?.getSearchContent(),
            ),
            pasteData.id,
        )
    }

    private suspend fun batchMarkDelete(doQuery: () -> Query<Long>) {
        val tasks = mutableListOf<Long>()
        var idList: List<Long>

        do {
            idList = database.transactionWithResult {
                val ids = doQuery().executeAsList()
                if (ids.isNotEmpty()) {
                    pasteDatabaseQueries.markDeletePasteData(ids)
                    tasks.addAll(ids.map { id -> taskDao.createTask(id, TaskType.DELETE_PASTE_TASK) })
                }
                ids
            }
        } while (idList.isNotEmpty())

        taskExecutor.submitTasks(tasks)
    }

    suspend fun markAllDeleteExceptFavorite(): Result<Unit> {
        return runCatching {
            batchMarkDelete {
                pasteDatabaseQueries.queryNoFavorite(markDeleteBatchNum)
            }
        }.onFailure { e ->
            logger.error(e) { "Mark all delete except favorite failed" }
        }
    }

    suspend fun markDeletePasteData(id: Long): Result<Unit> {
        return runCatching {
            val taskId = database.transactionWithResult {
                pasteDatabaseQueries.markDeletePasteData(listOf(id))
                taskDao.createTask(id, TaskType.DELETE_PASTE_TASK)
            }
            taskExecutor.submitTask(taskId)
        }.onFailure { e ->
            logger.error(e) { "Mark delete paste data failed" }
        }
    }

    fun deletePasteData(id: Long) {
        getDeletePasteData(id)?.clear(userDataPathProvider)
        pasteDatabaseQueries.deletePasteData(listOf(id))
    }

    fun getPasteDataFlow(limit: Long): Flow<List<PasteData>> {
        return pasteDatabaseQueries.getPasteDataListLimit(limit, PasteData::mapper)
            .asFlow()
            .map { it.executeAsList() }
            .catch { e ->
                logger.error(e) { "Error executing getPasteDataFlow query: ${e.message}" }
                emit(listOf())
            }
    }

    fun getAllTagsFlow(): Flow<List<PasteTag>> {
        return tagDatabaseQueries.getAllTags(PasteTag::mapper)
            .asFlow()
            .map { it.executeAsList() }
            .catch { e ->
                logger.error(e) { "Error executing getAllTagsFlow query: ${e.message}" }
                emit(listOf())
            }
    }

    fun getMaxSortOrder(): Long {
        return tagDatabaseQueries.maxSortOrder().executeAsOne()
    }

    fun createPasteTag(name: String, color: Long): Long {
        return database.transactionWithResult {
            val maxSortOrder = tagDatabaseQueries.maxSortOrder().executeAsOne()
            val newSortOrder = maxSortOrder + 1
            tagDatabaseQueries.createTag(name, color, newSortOrder)
            tagDatabaseQueries.getLastId().executeAsOne()
        }
    }

    fun updatePasteTagName(id: Long, name: String) {
        tagDatabaseQueries.updateTagName(name, id)
    }

    fun updatePasteTagColor(id: Long, color: Long) {
        tagDatabaseQueries.updateTagColor(color, id)
    }

    private fun pinPasteTag(pasteDataId: Long, pasteTagId: Long) {
        tagDatabaseQueries.pinPasteTag(pasteDataId, pasteTagId)
    }

    private fun unPinPasteTag(pasteDataId: Long, pasteTagId: Long) {
        tagDatabaseQueries.unPinPasteTag(pasteDataId, pasteTagId)
    }

    fun switchPinPasteTag(pasteDataId: Long, pasteTagId: Long) {
        database.transaction {
            val pinned = tagDatabaseQueries.isPinnedPasteTag(pasteDataId, pasteTagId).executeAsOne()
            if (pinned) {
                unPinPasteTag(pasteDataId, pasteTagId)
            } else {
                pinPasteTag(pasteDataId, pasteTagId)
            }
        }
    }

    fun getPasteTags(pasteDataId: Long): List<Long> {
        return tagDatabaseQueries.getPasteTags(pasteDataId).executeAsList()
    }

    fun deletePasteTag(id: Long) {
        tagDatabaseQueries.deleteTag(id)
    }

    @OptIn(ExperimentalTime::class)
    private fun markDeleteSameHash(
        newPasteDataId: Long,
        newPasteDataType: Int,
        newPasteDataHash: String,
    ): List<Long> {
        if (newPasteDataHash.isEmpty()) {
            return listOf()
        }

        val idList = pasteDatabaseQueries.getSameHashPasteDataIds(
            newPasteDataHash,
            newPasteDataType.toLong(),
            DateUtils.getOffsetDay(days = -1),
            newPasteDataId,
        ).executeAsList()

        return database.transactionWithResult {
            pasteDatabaseQueries.markDeletePasteData(idList)
            idList.map { id -> taskDao.createTask(id, TaskType.DELETE_PASTE_TASK) }
        }
    }

    suspend fun markDeleteByCleanTime(
        cleanTime: Long,
        pasteType: Int? = null,
    ) {
        batchMarkDelete {
            pasteDatabaseQueries.queryByCleanTime(cleanTime, pasteType?.toLong(), markDeleteBatchNum)
        }
    }

    fun getSize(allOrFavorite: Boolean = false): Long {
        return if (allOrFavorite) {
            pasteDatabaseQueries.getSize().executeAsOne().SUM ?: 0L
        } else {
            pasteDatabaseQueries.getFavoriteSize().executeAsOne().SUM ?: 0L
        }
    }

    fun getMinPasteDataCreateTime(): Long? {
        return pasteDatabaseQueries.getMinCreateTime().executeAsOneOrNull()?.MIN
    }

    fun updateCreateTime(id: Long) {
        pasteDatabaseQueries.updateCreateTime(id = id, time = DateUtils.nowEpochMilliseconds())
    }

    fun updatePasteAppearItem(
        id: Long,
        pasteItem: PasteItem,
        pasteSearchContent: String,
        addedSize: Long = 0L,
    ): Result<Unit> {
        database.transactionWithResult {
            pasteDatabaseQueries.updatePasteAppearItem(
                id = id,
                pasteAppearItem = pasteItem.toJson(),
                pasteSearchContent = pasteSearchContent,
                addedSize = addedSize,
                hash = pasteItem.hash,
            )
            pasteDatabaseQueries.change().executeAsOne() > 0
        }.let { changed ->
            return if (changed) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Update paste appear item failed for id=$id"))
            }
        }
    }

    fun updatePasteState(id: Long, pasteState: Int) {
        pasteDatabaseQueries.updatePasteDataState(pasteState.toLong(), id)
    }

    fun getSizeByTimeLessThan(time: Long): Long {
        return pasteDatabaseQueries.getSizeByTimeLessThan(time).executeAsOne().SUM ?: 0L
    }

    private fun createSearchPasteQuery(
        searchTerms: List<String>,
        local: Boolean? = null,
        favorite: Boolean? = null,
        pasteType: Int? = null,
        sort: Boolean = true,
        tagId: Long? = null,
        limit: Int,
    ): Query<PasteData> {
        val appInstanceId: String? = local?.let { appInfo.appInstanceId }

        return if (searchTerms.isNotEmpty()) {
            val searchQuery = "pasteSearchContent:(${searchTerms.joinToString(" AND ") { "$it*" }})"
            logger.info { "Creating paste query: $searchQuery" }

            pasteDatabaseQueries.complexSearch(
                local = local == true,
                appInstanceId = appInstanceId,
                favorite = favorite,
                pasteType = pasteType?.toLong(),
                searchQuery = searchQuery,
                sort = sort,
                tagId = tagId,
                number = limit.toLong(),
                mapper = PasteData::mapper,
            )
        } else {
            logger.info { "Creating paste simpleSearch" }

            pasteDatabaseQueries.simpleSearch(
                local = local == true,
                appInstanceId = appInstanceId,
                favorite = favorite,
                pasteType = pasteType?.toLong(),
                sort = sort,
                tagId = tagId,
                number = limit.toLong(),
                mapper = PasteData::mapper,
            )
        }
    }

    fun searchPasteData(
        searchTerms: List<String>,
        local: Boolean? = null,
        favorite: Boolean? = null,
        pasteType: Int? = null,
        sort: Boolean = true,
        tag: Long? = null,
        limit: Int,
    ): List<PasteData> {
        return logExecutionTime(logger, "searchPasteData") {
            logger.info { "Performing search for: $searchTerms" }
            createSearchPasteQuery(searchTerms, local, favorite, pasteType, sort, tag, limit).executeAsList()
        }
    }

    fun searchPasteDataFlow(
        searchTerms: List<String>,
        local: Boolean? = null,
        favorite: Boolean? = null,
        pasteType: Int? = null,
        sort: Boolean = true,
        tag: Long? = null,
        limit: Int,
    ): Flow<List<PasteData>> {
        return logExecutionTime(logger, "searchPasteData") {
            logger.info { "Performing search for: $searchTerms" }
            createSearchPasteQuery(searchTerms, local, favorite, pasteType, sort, tag, limit)
                .asFlow()
                .map { it.executeAsList() }
                .catch { e ->
                    logger.error(e) { "Error executing search query: ${e.message}\n" +
                            "searchTerms=$searchTerms local=$local favorite=$favorite " +
                            "pasteType=$pasteType sort=$sort" }
                    emit(searchPasteData(listOf(), local, favorite, pasteType, sort, tag, limit))
                }
        }
    }

    fun searchBySource(source: String): List<PasteData> {
        return pasteDatabaseQueries.searchBySource(source, mapper = PasteData::mapper)
            .executeAsList()
    }

    suspend fun releaseRemotePasteData(
        pasteData: PasteData,
        tryWritePasteboard: (PasteData) -> Unit,
    ): Result<Unit> {
        return runCatching {
            val remotePasteDataId = pasteData.id
            val tasks = mutableListOf<Long>()
            val existFile = pasteData.existFileCategory()
            val existIconFile: Boolean? =
                pasteData.source?.let {
                    fileUtils.existFile(userDataPathProvider.resolve("$it.png", AppFileType.ICON))
                }

            val pasteState = if (existFile) {
                PasteState.LOADING
            } else {
                PasteState.LOADED
            }

            val id = createPasteData(pasteData, pasteState)

            if (!existFile) {
                tasks.addAll(markDeleteSameHash(id, pasteData.pasteType, pasteData.hash))
                addRenderingTask(id, pasteData.getType()) {
                    tasks.add(it)
                }
            } else {
                val pasteCoordinate = pasteData.getPasteCoordinate(id)
                val pasteAppearItem = pasteData.pasteAppearItem
                val pasteCollection = pasteData.pasteCollection

                val newPasteAppearItem = pasteAppearItem?.bind(pasteCoordinate)
                val newPasteCollection = pasteCollection.bind(pasteCoordinate)

                val newPasteData = pasteData.copy(
                    id = id,
                    pasteAppearItem = newPasteAppearItem,
                    pasteCollection = newPasteCollection,
                )

                updateFilePath(newPasteData)

                tasks.add(
                    taskDao.createTask(
                        id,
                        TaskType.PULL_FILE_TASK,
                        PullExtraInfo(remotePasteDataId),
                    ),
                )
            }

            existIconFile?.let {
                if (!it) {
                    tasks.add(taskDao.createTask(id, TaskType.PULL_ICON_TASK))
                }
            }

            if (!existFile) {
                tryWritePasteboard(pasteData)
            }
            taskExecutor.submitTasks(tasks)
        }.onFailure { e ->
            logger.error(e) { "Release remote paste data failed" }
        }
    }

    suspend fun releaseRemotePasteDataWithFile(
        id: Long,
        tryWritePasteboard: (PasteData) -> Unit,
    ): Result<Unit> {
        return runCatching {
            val tasks = mutableListOf<Long>()
            database.transactionWithResult {
                updatePasteState(id, PasteState.LOADED)
                getNoDeletePasteData(id)
            }?.let { pasteData ->
                tasks.addAll(markDeleteSameHash(id, pasteData.pasteType, pasteData.hash))
                tryWritePasteboard(pasteData)
            }
            taskExecutor.submitTasks(tasks)
        }.onFailure { e ->
            logger.error(e) { "Release remote paste data with file failed" }
        }
    }

    suspend fun releaseLocalPasteData(id: Long, pasteItems: List<PasteItem>) {
        getLoadingPasteData(id)?.let { pasteData ->
            var pasteAppearItems = pasteItems
            for (pastePlugin in pasteProcessPlugins) {
                pasteAppearItems = pastePlugin.process(
                    pasteData.getPasteCoordinate(),
                    pasteAppearItems,
                    pasteData.source,
                )
            }

            if (pasteAppearItems.isEmpty()) {
                markDeletePasteData(id)
                return@let
            }

            val size = pasteAppearItems.sumOf { it.size }
            val maxFileSize = pasteAppearItems.filter { it is PasteFiles }
                .maxByOrNull { it.size }
                ?.size ?: 0
            // first item as pasteAppearItem
            // remaining items as pasteContent
            val firstItem: PasteItem = pasteAppearItems.first()
            val remainingItems: List<PasteItem> = pasteAppearItems.drop(1)

            val hash = firstItem.hash
            val pasteType = firstItem.getPasteType()

            val change = database.transactionWithResult {
                pasteDatabaseQueries.updatePasteDataToLoaded(
                    pasteAppearItem = firstItem.toJson(),
                    pasteCollection = PasteCollection(remainingItems).toJson(),
                    pasteType = pasteType.type.toLong(),
                    pasteSearchContent = searchContentService.createSearchContent(
                        pasteData.source,
                        firstItem.getSearchContent(),
                    ),
                    size = size,
                    hash = hash,
                    id = id,
                )
                pasteDatabaseQueries.change().executeAsOne() > 0
            }

            if (change) {
                val tasks = mutableListOf<Long>()
                addRenderingTask(id, pasteType) {
                    tasks.add(it)
                }
                if (appControl.isFileSizeSyncEnabled(maxFileSize)) {
                    tasks.add(
                        taskDao.createTask(
                            id,
                            TaskType.SYNC_PASTE_TASK,
                            SyncExtraInfo()
                        )
                    )
                }
                if (pasteType.isFile() || pasteType.isImage()) {
                    if ((firstItem as PasteFiles).isRefFiles()) {
                        tasks.addAll(markDeleteSameHash(id, pasteType.type, hash))
                    }
                } else {
                    tasks.addAll(markDeleteSameHash(id, pasteType.type, hash))
                }
                currentPaste.setPasteId(id)
                taskExecutor.submitTasks(tasks)
            }
        }
    }

    private fun addRenderingTask(id: Long, pasteType: PasteType, addTask: (Long) -> Unit) {
        if (pasteType.isUrl()) {
            addTask(taskDao.createTask(id, TaskType.OPEN_GRAPH_TASK))
        }
    }

    fun getPasteResourceInfo(favorite: Boolean? = null): PasteResourceInfo {
        val builder = PasteResourceInfoBuilder()
        val doAdd: (PasteData) -> Unit = { pasteData ->
            if (favorite == null || favorite == pasteData.favorite) {
                pasteData.pasteAppearItem?.let {
                    builder.add(it)
                }
                for (item in pasteData.pasteCollection.pasteItems) {
                    builder.add(item)
                }
            }
        }
        batchReadPasteData(
            readPasteDataList = { id, limit ->
                pasteDatabaseQueries.getBatchPasteData(id, limit, PasteData::mapper)
                    .executeAsList()
            },
            dealPasteData = doAdd)
        return builder.build()
    }

    fun batchReadPasteData(
        batchNum: Long = 1000L,
        readPasteDataList: (Long, Long) -> List<PasteData>,
        dealPasteData: (PasteData) -> Unit): Long {
        var id = -1L
        var count = 0L
        do {
            val pasteDataList = readPasteDataList(id, batchNum)
            for (pasteData in pasteDataList) {
                dealPasteData(pasteData)
            }
            count += pasteDataList.size
            pasteDataList.lastOrNull()?.id?.let { id = it }
        } while (pasteDataList.isNotEmpty())
        return count
    }

    fun getExportPasteData(
        id: Long,
        limit: Long,
        pasteExportParam: PasteExportParam,
    ): List<PasteData>  {
        return pasteDatabaseQueries.getBatchExportPasteData(
            id,
            pasteExportParam.types,
            pasteExportParam.onlyFavorite,
            limit,
            PasteData::mapper,
        ).executeAsList()
    }

    fun getExportNum(pasteExportParam: PasteExportParam): Long {
        return pasteDatabaseQueries.getExportNum(
            pasteExportParam.types,
            pasteExportParam.onlyFavorite,
        ).executeAsOne()
    }
}
