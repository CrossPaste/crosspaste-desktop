package com.crosspaste.db.paste

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import com.crosspaste.Database
import com.crosspaste.app.AppInfo
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteExportParam
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.LoggerExtension.logExecutionTime
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PasteDao(
    private val appInfo: AppInfo,
    private val database: Database,
    private val searchContentService: SearchContentService,
    private val taskSubmitter: TaskSubmitter,
    private val userDataPathProvider: UserDataPathProvider,
) {

    private val logger = KotlinLogging.logger {}

    private val pasteDatabaseQueries = database.pasteDatabaseQueries

    private val markDeleteBatchNum = 50L

    fun getNoDeletePasteDataBlock(id: Long): PasteData? {
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

    suspend fun getLatestLoadedPasteData(): PasteData? = withContext(ioDispatcher) {
        pasteDatabaseQueries.getLatestLoadedPasteData(PasteData::mapper)
            .executeAsOneOrNull()
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

    fun getSameHashPasteDataIds(hash: String, pasteType: Int, excludeId: Long): List<Long> {
        return pasteDatabaseQueries.getSameHashPasteDataIds(
            hash,
            pasteType.toLong(),
            DateUtils.getOffsetDay(days = -1),
            excludeId,
        ).executeAsList()
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

    suspend fun getDistinctSources(): List<String> = withContext(ioDispatcher) {
        pasteDatabaseQueries.getDistinctSources(appInfo.appInstanceId)
            .executeAsList()
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
