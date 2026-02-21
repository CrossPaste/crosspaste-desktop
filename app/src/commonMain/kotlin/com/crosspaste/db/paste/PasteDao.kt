package com.crosspaste.db.paste

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import com.crosspaste.Database
import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteExportParam
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemProperties
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.task.TaskBuilder
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.LoggerExtension.logExecutionTime
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime

class PasteDao(
    private val appInfo: AppInfo,
    private val commonConfigManager: CommonConfigManager,
    private val currentPaste: CurrentPaste,
    private val database: Database,
    private val pasteProcessPlugins: List<PasteProcessPlugin>,
    private val searchContentService: SearchContentService,
    private val taskSubmitter: TaskSubmitter,
    private val userDataPathProvider: UserDataPathProvider,
) {

    companion object {
        private val fileUtils = getFileUtils()
    }

    private val logger = KotlinLogging.logger {}

    private val pasteDatabaseQueries = database.pasteDatabaseQueries

    private val tagDatabaseQueries = database.tagDatabaseQueries

    private val markDeleteBatchNum = 50L

    private fun getNoDeletePasteDataBlock(id: Long): PasteData? {
        return pasteDatabaseQueries.getPasteData(
            id,
            listOf(PasteState.LOADING.toLong(), PasteState.LOADED.toLong()),
            PasteData::mapper,
        ).executeAsOneOrNull()
    }

    @Suppress("unused")
    fun getNoDeletePasteDataFlow(id: Long): Flow<PasteData?> {
        return pasteDatabaseQueries.getPasteData(
            id,
            listOf(PasteState.LOADING.toLong(), PasteState.LOADED.toLong()),
            PasteData::mapper,
        ).asFlow()
            .map { it.executeAsOneOrNull() }
            .catch { e ->
                logger.error(e) { "Error executing getNoDeletePasteDataFlow query: ${e.message}" }
                emit(null)
            }
            .flowOn(ioDispatcher)
    }

    suspend fun getNoDeletePasteData(id: Long): PasteData? = withContext(ioDispatcher) {
        getNoDeletePasteDataBlock(id)
    }

    suspend fun getLoadingPasteData(id: Long): PasteData? = withContext(ioDispatcher) {
        pasteDatabaseQueries.getPasteData(
            id,
            listOf(PasteState.LOADING.toLong()),
            PasteData::mapper,
        ).executeAsOneOrNull()
    }

    fun getLoadedPasteDataBlock(id: Long): PasteData? {
        return pasteDatabaseQueries.getPasteData(
            id,
            listOf(PasteState.LOADED.toLong()),
            PasteData::mapper,
        ).executeAsOneOrNull()
    }

    suspend fun getDeletePasteData(id: Long): PasteData? = withContext(ioDispatcher) {
        pasteDatabaseQueries.getPasteData(
            id,
            listOf(PasteState.DELETED.toLong()),
            PasteData::mapper,
        ).executeAsOneOrNull()
    }

    suspend fun setFavorite(
        pasteId: Long,
        favorite: Boolean,
    ): Unit = withContext(ioDispatcher) {
        pasteDatabaseQueries.updateFavorite(favorite, pasteId)
    }

    suspend fun createPasteData(pasteData: PasteData, pasteState: Int? = null): Long = withContext(ioDispatcher) {
        database.transactionWithResult {
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

    suspend fun updateFilePath(pasteData: PasteData) = withContext(ioDispatcher) {
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

    private suspend fun batchMarkDelete(doQuery: () -> Query<Long>) = withContext(ioDispatcher) {
        taskSubmitter.submit {
            var idList: List<Long>
            do {
                idList = database.transactionWithResult {
                    val ids = doQuery().executeAsList()
                    if (ids.isNotEmpty()) {
                        pasteDatabaseQueries.markDeletePasteData(ids)
                        addDeletePasteTasks(ids)
                    }
                    ids
                }
            } while (idList.isNotEmpty())
        }
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

    suspend fun markDeletePasteData(id: Long): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            taskSubmitter.submit {
                database.transaction {
                    pasteDatabaseQueries.markDeletePasteData(listOf(id))
                    addDeletePasteTasks(listOf(id))
                }
            }
        }.onFailure { e ->
            logger.error(e) { "Mark delete paste data failed" }
        }
    }

    suspend fun cutPasteData(id: Long, delayMillis: Long) = withContext(ioDispatcher) {
        runCatching {
            taskSubmitter.submit {
                database.transaction {
                    pasteDatabaseQueries.markDeletePasteData(listOf(id))
                    addDelayedDeletePasteTask(id, delayMillis)
                }
            }
        }.onFailure { e ->
            logger.error(e) { "Cut paste data failed" }
        }
    }

    suspend fun deletePasteData(id: Long) = withContext(ioDispatcher) {
        getDeletePasteData(id)?.let {
            it.clear(userDataPathProvider)
            pasteDatabaseQueries.deletePasteData(listOf(id))
        }
    }

    @Suppress("unused")
    fun getPasteDataFlow(limit: Long): Flow<List<PasteData>> {
        return pasteDatabaseQueries.getPasteDataListLimit(limit, PasteData::mapper)
            .asFlow()
            .map { it.executeAsList() }
            .catch { e ->
                logger.error(e) { "Error executing getPasteDataFlow query: ${e.message}" }
                emit(listOf())
            }
            .flowOn(ioDispatcher)
    }

    fun getAllTagsFlow(): Flow<List<PasteTag>> {
        return tagDatabaseQueries.getAllTags(PasteTag::mapper)
            .asFlow()
            .map { it.executeAsList() }
            .catch { e ->
                logger.error(e) { "Error executing getAllTagsFlow query: ${e.message}" }
                emit(listOf())
            }
            .flowOn(ioDispatcher)
    }

    suspend fun getMaxSortOrder(): Long = withContext(ioDispatcher) {
        tagDatabaseQueries.maxSortOrder().executeAsOne()
    }

    suspend fun createPasteTag(name: String, color: Long): Long = withContext(ioDispatcher) {
        database.transactionWithResult {
            val maxSortOrder = tagDatabaseQueries.maxSortOrder().executeAsOne()
            val newSortOrder = maxSortOrder + 1
            tagDatabaseQueries.createTag(name, color, newSortOrder)
            tagDatabaseQueries.getLastId().executeAsOne()
        }
    }

    suspend fun updatePasteTagName(id: Long, name: String) = withContext(ioDispatcher) {
        tagDatabaseQueries.updateTagName(name, id)
    }

    suspend fun updatePasteTagColor(id: Long, color: Long) = withContext(ioDispatcher) {
        tagDatabaseQueries.updateTagColor(color, id)
    }

    private fun pinPasteTag(pasteDataId: Long, pasteTagId: Long) {
        tagDatabaseQueries.pinPasteTag(pasteDataId, pasteTagId)
    }

    private fun unPinPasteTag(pasteDataId: Long, pasteTagId: Long) {
        tagDatabaseQueries.unPinPasteTag(pasteDataId, pasteTagId)
    }

    fun switchPinPasteTagBlock(pasteDataId: Long, pasteTagId: Long) {
        database.transaction {
            val pinned = tagDatabaseQueries.isPinnedPasteTag(pasteDataId, pasteTagId).executeAsOne()
            if (pinned) {
                unPinPasteTag(pasteDataId, pasteTagId)
            } else {
                pinPasteTag(pasteDataId, pasteTagId)
            }
        }
    }

    fun getPasteTagsBlock(pasteDataId: Long): List<Long>  {
        return tagDatabaseQueries.getPasteTags(pasteDataId).executeAsList()
    }

    fun deletePasteTagBlock(id: Long) {
        tagDatabaseQueries.deleteTag(id)
    }

    @OptIn(ExperimentalTime::class)
    private fun TaskBuilder.markDeleteSameHash(
        newPasteDataId: Long,
        newPasteDataType: Int,
        newPasteDataHash: String,
    ) {
        if (newPasteDataHash.isEmpty()) {
            return
        }

        val idList = pasteDatabaseQueries.getSameHashPasteDataIds(
            newPasteDataHash,
            newPasteDataType.toLong(),
            DateUtils.getOffsetDay(days = -1),
            newPasteDataId,
        ).executeAsList()

        database.transaction {
            pasteDatabaseQueries.markDeletePasteData(idList)
            addDeletePasteTasks(idList)
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

    suspend fun getActiveCount(): Long = withContext(ioDispatcher) {
        pasteDatabaseQueries.getActiveCount().executeAsOne()
    }

    suspend fun getSize(allOrFavorite: Boolean = false): Long = withContext(ioDispatcher) {
        if (allOrFavorite) {
            pasteDatabaseQueries.getSize().executeAsOne().SUM ?: 0L
        } else {
            pasteDatabaseQueries.getFavoriteSize().executeAsOne().SUM ?: 0L
        }
    }

    suspend fun getMinPasteDataCreateTime(): Long? = withContext(ioDispatcher) {
        pasteDatabaseQueries.getMinCreateTime().executeAsOneOrNull()?.MIN
    }

    suspend fun updateCreateTime(id: Long): Unit = withContext(ioDispatcher) {
        pasteDatabaseQueries.updateCreateTime(id = id, time = DateUtils.nowEpochMilliseconds())
    }

    suspend fun updatePasteAppearItem(
        id: Long,
        pasteItem: PasteItem,
        pasteSearchContent: String,
        addedSize: Long = 0L,
    ): Result<Unit> = withContext(ioDispatcher) {
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
            if (changed) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Update paste appear item failed for id=$id"))
            }
        }
    }

    suspend fun updatePasteState(id: Long, pasteState: Int) = withContext(ioDispatcher) {
        pasteDatabaseQueries.updatePasteDataState(pasteState.toLong(), id)
    }

    suspend fun getSizeByTimeLessThan(time: Long): Long = withContext(ioDispatcher) {
        pasteDatabaseQueries.getSizeByTimeLessThan(time).executeAsOne().SUM ?: 0L
    }

    suspend fun findCleanTimeByCumulativeSize(targetSize: Long): Long? = withContext(ioDispatcher) {
        var cumulativeSize = 0L
        var afterTime = -1L
        var afterId = -1L
        val batchSize = 500L
        while (true) {
            val batch = pasteDatabaseQueries
                .getOldestNonFavoriteCreateTimeAndSize(afterTime, afterId, batchSize)
                .executeAsList()
            if (batch.isEmpty()) break
            for (row in batch) {
                cumulativeSize += row.size
                if (cumulativeSize >= targetSize) {
                    return@withContext row.createTime
                }
            }
            val last = batch.last()
            afterTime = last.createTime
            afterId = last.id
        }
        null
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

    suspend fun searchPasteData(
        searchTerms: List<String>,
        local: Boolean? = null,
        favorite: Boolean? = null,
        pasteType: Int? = null,
        sort: Boolean = true,
        tag: Long? = null,
        limit: Int,
    ): List<PasteData> = withContext(ioDispatcher) {
        logExecutionTime(logger, "searchPasteData") {
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
                .flowOn(ioDispatcher)
        }
    }

    suspend fun searchBySource(source: String): List<PasteData> = withContext(ioDispatcher) {
        pasteDatabaseQueries.searchBySource(source, mapper = PasteData::mapper)
            .executeAsList()
    }

    suspend fun releaseRemotePasteData(
        pasteData: PasteData,
        tryWritePasteboard: (PasteData) -> Unit,
    ): Result<Unit> {
        return runCatching {
            val remotePasteDataId = pasteData.id
            val isFileType = pasteData.isFileType()
            val existIconFile: Boolean? =
                pasteData.source?.let {
                    fileUtils.existFile(userDataPathProvider.resolve("$it.png", AppFileType.ICON))
                }

            val pasteState = if (isFileType) {
                PasteState.LOADING
            } else {
                PasteState.LOADED
            }

            val id = createPasteData(pasteData, pasteState)

            taskSubmitter.submit {
                if (!isFileType) {
                    markDeleteSameHash(id, pasteData.pasteType, pasteData.hash)
                    addRenderingTask(id, pasteData.getType())
                    tryWritePasteboard(pasteData)
                } else {
                    val pasteFiles = pasteData.getPasteItem(PasteFiles::class)

                    if (pasteFiles == null) {
                        logger.warn { "File-type paste $id has no PasteFiles item, skipping" }
                        return@submit
                    }

                    val fileSize = pasteFiles.size
                    val maxBackupFileSize =
                        fileUtils.bytesSize(
                            commonConfigManager.getCurrentConfig().maxBackupFileSize,
                        )

                    val syncToDownload =
                        fileSize > maxBackupFileSize ||
                            pasteData.pasteAppearItem?.extraInfo
                                ?.get(PasteItemProperties.SYNC_TO_DOWNLOAD)
                                ?.jsonPrimitive?.booleanOrNull == true

                    val pasteCoordinate = pasteData.getPasteCoordinate(id)
                    val pasteAppearItem = pasteData.pasteAppearItem
                    val pasteCollection = pasteData.pasteCollection

                    val newPasteAppearItem = pasteAppearItem?.bind(pasteCoordinate, syncToDownload)
                    val newPasteCollection = pasteCollection.bind(pasteCoordinate, syncToDownload)

                    val newPasteData = pasteData.copy(
                        id = id,
                        pasteAppearItem = newPasteAppearItem,
                        pasteCollection = newPasteCollection,
                    )

                    updateFilePath(newPasteData)

                    addPullFileTask(id, remotePasteDataId)
                    addRelaySyncTask(id, newPasteData.appInstanceId)
                }

                existIconFile?.let {
                    addPullIconTask(id, it)
                }
            }
        }.onFailure { e ->
            logger.error(e) { "Release remote paste data failed" }
        }
    }

    suspend fun releaseRemotePasteDataWithFile(
        id: Long,
        tryWritePasteboard: (PasteData) -> Unit,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            taskSubmitter.submit {
                database.transactionWithResult {
                    pasteDatabaseQueries.updatePasteDataState(PasteState.LOADED.toLong(), id)
                    getNoDeletePasteDataBlock(id)
                }?.let {
                    markDeleteSameHash(id, it.pasteType, it.hash)
                    addRelaySyncTask(id, it.appInstanceId)
                    tryWritePasteboard(it)
                }
            }
        }.onFailure { e ->
            logger.error(e) { "Release remote paste data with file failed" }
        }
    }

    suspend fun releaseLocalPasteData(id: Long, pasteItems: List<PasteItem>) = withContext(ioDispatcher) {
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
                currentPaste.setPasteId(id)
                taskSubmitter.submit {
                    addRenderingTask(id, pasteType)
                    addSyncTask(id, pasteData.appInstanceId, maxFileSize)

                    if (pasteType.isFile() || pasteType.isImage()) {
                        if ((firstItem as PasteFiles).isRefFiles()) {
                            markDeleteSameHash(id, pasteType.type, hash)
                        }
                    } else {
                        markDeleteSameHash(id, pasteType.type, hash)
                    }
                }
            }
        }
    }

    suspend fun getPasteResourceInfo(favorite: Boolean? = null): PasteResourceInfo = withContext(ioDispatcher) {
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
        builder.build()
    }

    suspend fun batchReadPasteData(
        batchNum: Long = 1000L,
        readPasteDataList: suspend (Long, Long) -> List<PasteData>,
        dealPasteData: (PasteData) -> Unit): Long = withContext(ioDispatcher) {
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
        count
    }

    suspend fun getExportPasteData(
        id: Long,
        limit: Long,
        pasteExportParam: PasteExportParam,
    ): List<PasteData> = withContext(ioDispatcher) {
        pasteDatabaseQueries.getBatchExportPasteData(
            id,
            pasteExportParam.types,
            pasteExportParam.onlyFavorite,
            limit,
            PasteData::mapper,
        ).executeAsList()
    }

    suspend fun getExportNum(pasteExportParam: PasteExportParam): Long = withContext(ioDispatcher) {
        pasteDatabaseQueries.getExportNum(
            pasteExportParam.types,
            pasteExportParam.onlyFavorite,
        ).executeAsOne()
    }
}
