package com.crosspaste.db.paste

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import com.crosspaste.Database
import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.config.ConfigManager
import com.crosspaste.db.task.PullExtraInfo
import com.crosspaste.db.task.SyncExtraInfo
import com.crosspaste.db.task.TaskDao
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.db.task.TaskType
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.PasteExportParam
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.task.TaskExecutor
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.LoggerExtension.logExecutionTime
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PasteDao(
    private val appInfo: AppInfo,
    private val configManager: ConfigManager,
    private val currentPaste: CurrentPaste,
    private val database: Database,
    private val lazyTaskExecutor: Lazy<TaskExecutor>,
    private val pasteProcessPlugins: List<PasteProcessPlugin>,
    private val taskDao: TaskDao,
    private val userDataPathProvider: UserDataPathProvider,
) {

    companion object {
        private val fileUtils = getFileUtils()
    }

    val logger = KotlinLogging.logger {}

    private val pasteDatabaseQueries = database.pasteDatabaseQueries

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
                pasteData.pasteSearchContent,
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
            pasteData.pasteSearchContent,
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

    suspend fun markAllDeleteExceptFavorite() {
        batchMarkDelete {
            pasteDatabaseQueries.queryNoFavorite(markDeleteBatchNum)
        }
    }

    suspend fun markDeletePasteData(id: Long) {
        val taskId = database.transactionWithResult {
            pasteDatabaseQueries.markDeletePasteData(listOf(id))
            taskDao.createTask(id, TaskType.DELETE_PASTE_TASK)
        }
        taskExecutor.submitTask(taskId)
    }

    fun deletePasteData(id: Long) {
        database.transaction {
            getDeletePasteData(id)?.clear(userDataPathProvider)
            pasteDatabaseQueries.deletePasteData(listOf(id))
        }
    }

    fun getPasteDataFlow(limit: Long): Flow<List<PasteData>> {
        return pasteDatabaseQueries.getPasteDataListLimit(limit, PasteData::mapper)
            .asFlow()
            .map { it.executeAsList() }
    }

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

    fun updatePasteAppearItem(id: Long, pasteItem: PasteItem) {
        pasteDatabaseQueries.updatePasteAppearItem(id = id, pasteAppearItem = pasteItem.toJson())
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
        limit: Int,
    ): Query<PasteData> {
        val appInstanceId: String? = local?.let { appInfo.appInstanceId }
        val searchQuery = "pasteSearchContent:(${searchTerms.joinToString(" AND ") { "\"$it\"" }})"

        return if (searchTerms.isNotEmpty()) {
            pasteDatabaseQueries.complexSearch(
                local = local == true,
                appInstanceId = appInstanceId,
                favorite = favorite,
                pasteType = pasteType?.toLong(),
                searchQuery = searchQuery,
                sort = sort,
                number = limit.toLong(),
                mapper = PasteData::mapper,
            )
        } else {
            pasteDatabaseQueries.simpleSearch(
                local = local == true,
                appInstanceId = appInstanceId,
                favorite = favorite,
                pasteType = pasteType?.toLong(),
                sort = sort,
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
        limit: Int,
    ): List<PasteData> {
        return logExecutionTime(logger, "searchPasteData") {
            logger.info { "Performing search for: $searchTerms" }
            createSearchPasteQuery(searchTerms, local, favorite, pasteType, sort, limit).executeAsList()
        }
    }

    fun searchPasteDataFlow(
        searchTerms: List<String>,
        local: Boolean? = null,
        favorite: Boolean? = null,
        pasteType: Int? = null,
        sort: Boolean = true,
        limit: Int,
    ): Flow<List<PasteData>> {
        return logExecutionTime(logger, "searchPasteData") {
            logger.info { "Performing search for: $searchTerms" }
            createSearchPasteQuery(searchTerms, local, favorite, pasteType, sort, limit)
                .asFlow()
                .map { it.executeAsList() }
        }
    }

    suspend fun releaseRemotePasteData(
        pasteData: PasteData,
        tryWritePasteboard: (PasteData, Boolean) -> Unit,
    ) {
        val remotePasteDataId = pasteData.id
        val tasks = mutableListOf<Long>()
        val existFile = pasteData.existFileResource()
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
            if (pasteData.getType().isHtml()) {
                tasks.add(taskDao.createTask(id, TaskType.HTML_TO_IMAGE_TASK))
            } else if (pasteData.getType().isRtf()) {
                tasks.add(taskDao.createTask(id, TaskType.RTF_TO_IMAGE_TASK))
            }
        } else {
            val pasteCoordinate = pasteData.getPasteCoordinate()
            val pasteAppearItem = pasteData.pasteAppearItem
            val pasteCollection = pasteData.pasteCollection
            val source = pasteData.source

            val newPasteAppearItem = pasteAppearItem?.bind(pasteCoordinate)
            val newPasteCollection = pasteCollection.bind(pasteCoordinate)

            val newPasteData = pasteData.copy(
                id = id,
                pasteAppearItem = newPasteAppearItem,
                pasteCollection = newPasteCollection,
                pasteSearchContent = PasteData.createSearchContent(
                    source,
                    pasteAppearItem?.getSearchContent(),
                ),
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

        tryWritePasteboard(pasteData, existFile)
        taskExecutor.submitTasks(tasks)
    }

    suspend fun releaseRemotePasteDataWithFile(
        id: Long,
        tryWritePasteboard: (PasteData) -> Unit,
    ) {
        val tasks = mutableListOf<Long>()
        database.transactionWithResult {
            updatePasteState(id, PasteState.LOADED)
            getNoDeletePasteData(id)
        }?.let { pasteData ->
            tasks.addAll(markDeleteSameHash(id, pasteData.pasteType, pasteData.hash))
            tryWritePasteboard(pasteData)
        }
        taskExecutor.submitTasks(tasks)
    }

    suspend fun releaseLocalPasteData(id: Long, pasteItems: List<PasteItem>) {
        getLoadingPasteData(id)?.let { pasteData ->
                var pasteAppearItems = pasteItems
                for (pastePlugin in pasteProcessPlugins) {
                    pasteAppearItems = pastePlugin.process(pasteAppearItems, pasteData.source)
                }
                val size = pasteAppearItems.sumOf { it.size }
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
                        pasteSearchContent = PasteData.createSearchContent(
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
                    if (pasteType.isHtml()) {
                        tasks.add(taskDao.createTask(id, TaskType.HTML_TO_IMAGE_TASK))
                    } else if (pasteType.isRtf()) {
                        tasks.add(taskDao.createTask(id, TaskType.RTF_TO_IMAGE_TASK))
                    }
                    if (!configManager.config.enabledSyncFileSizeLimit ||
                        fileUtils.bytesSize(configManager.config.maxSyncFileSize) > size
                    ) {
                        tasks.add(taskDao.createTask(id, TaskType.SYNC_PASTE_TASK, SyncExtraInfo()))
                    }
                    tasks.addAll(markDeleteSameHash(id, pasteType.type, hash))
                    currentPaste.setPasteId(id)
                    taskExecutor.submitTasks(tasks)
                }
        }

    }

    fun getPasteResourceInfo(favorite: Boolean? = null): PasteResourceInfo {
        val builder = PasteResourceInfoBuilder()
        val doAdd: (PasteData) -> Unit = { pasteData ->
            favorite?.let {
                if (pasteData.favorite != it) {
                    return@let
                }
            }
            pasteData.pasteAppearItem?.let {
                builder.add(it)
            }
            for (item in pasteData.pasteCollection.pasteItems) {
                builder.add(item)
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
