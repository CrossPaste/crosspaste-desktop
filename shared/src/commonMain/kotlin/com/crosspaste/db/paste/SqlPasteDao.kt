package com.crosspaste.db.paste

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import com.crosspaste.Database
import com.crosspaste.app.AppInfo
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteExportParam
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.clear
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemReader
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

class SqlPasteDao(
    private val appInfo: AppInfo,
    private val database: Database,
    private val pasteItemReader: PasteItemReader,
    private val searchContentService: SearchContentService,
    private val taskSubmitter: TaskSubmitter,
    private val userDataPathProvider: UserDataPathProvider,
) : PasteDao {

    private val logger = KotlinLogging.logger {}

    private val pasteDatabaseQueries = database.pasteDatabaseQueries

    private val markDeleteBatchNum = 50L

    override fun getNoDeletePasteDataBlock(id: Long): PasteData? =
        pasteDatabaseQueries
            .getPasteData(
                id,
                listOf(PasteState.LOADING.toLong(), PasteState.LOADED.toLong()),
                PasteData::mapper,
            ).executeAsOneOrNull()

    @Suppress("unused")
    override fun getNoDeletePasteDataFlow(id: Long): Flow<PasteData?> =
        pasteDatabaseQueries
            .getPasteData(
                id,
                listOf(PasteState.LOADING.toLong(), PasteState.LOADED.toLong()),
                PasteData::mapper,
            ).asFlow()
            .map { it.executeAsOneOrNull() }
            .catch { e ->
                logger.error(e) { "Error executing getNoDeletePasteDataFlow query: ${e.message}" }
                emit(null)
            }.flowOn(ioDispatcher)

    override suspend fun getNoDeletePasteData(id: Long): PasteData? =
        withContext(ioDispatcher) {
            getNoDeletePasteDataBlock(id)
        }

    override suspend fun getLoadingPasteData(id: Long): PasteData? =
        withContext(ioDispatcher) {
            pasteDatabaseQueries
                .getPasteData(
                    id,
                    listOf(PasteState.LOADING.toLong()),
                    PasteData::mapper,
                ).executeAsOneOrNull()
        }

    override fun getLoadedPasteDataBlock(id: Long): PasteData? =
        pasteDatabaseQueries
            .getPasteData(
                id,
                listOf(PasteState.LOADED.toLong()),
                PasteData::mapper,
            ).executeAsOneOrNull()

    override suspend fun getLatestLoadedPasteData(): PasteData? =
        withContext(ioDispatcher) {
            pasteDatabaseQueries
                .getLatestLoadedPasteData(PasteData::mapper)
                .executeAsOneOrNull()
        }

    override suspend fun getDeletePasteData(id: Long): PasteData? =
        withContext(ioDispatcher) {
            pasteDatabaseQueries
                .getPasteData(
                    id,
                    listOf(PasteState.DELETED.toLong()),
                    PasteData::mapper,
                ).executeAsOneOrNull()
        }

    override suspend fun createPasteData(
        pasteData: PasteData,
        pasteState: Int?,
    ): Long =
        withContext(ioDispatcher) {
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
                        pasteData.pasteAppearItem?.let { pasteItemReader.getSearchContent(it) },
                    ),
                    (pasteState ?: pasteData.pasteState).toLong(),
                    pasteData.remote,
                )
                pasteDatabaseQueries.getLastId().executeAsOne()
            }
        }

    override suspend fun updateFilePath(pasteData: PasteData) {
        withContext(ioDispatcher) {
            pasteDatabaseQueries.updateRemotePasteDataWithFile(
                pasteData.pasteAppearItem?.toJson(),
                pasteData.pasteCollection.toJson(),
                searchContentService.createSearchContent(
                    pasteData.source,
                    pasteData.pasteAppearItem?.let { pasteItemReader.getSearchContent(it) },
                ),
                pasteData.id,
            )
        }
    }

    private suspend fun batchMarkDelete(doQuery: () -> Query<Long>) =
        withContext(ioDispatcher) {
            taskSubmitter.submit {
                var idList: List<Long>
                do {
                    idList =
                        database.transactionWithResult {
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

    override suspend fun markAllDeleteExceptTagged(): Result<Unit> =
        runCatching {
            batchMarkDelete {
                pasteDatabaseQueries.queryNoTag(markDeleteBatchNum)
            }
        }.onFailure { e ->
            logger.error(e) { "Mark all delete except tagged failed" }
        }

    override suspend fun markDeletePasteData(id: Long): Result<Unit> =
        withContext(ioDispatcher) {
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

    override suspend fun cutPasteData(
        id: Long,
        delayMillis: Long,
    ) {
        withContext(ioDispatcher) {
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
    }

    override suspend fun deletePasteData(id: Long) {
        withContext(ioDispatcher) {
            getDeletePasteData(id)?.let {
                it.clear(userDataPathProvider)
                pasteDatabaseQueries.deletePasteData(listOf(id))
            }
        }
    }

    @Suppress("unused")
    override fun getPasteDataFlow(limit: Long): Flow<List<PasteData>> =
        pasteDatabaseQueries
            .getPasteDataListLimit(limit, PasteData::mapper)
            .asFlow()
            .map { it.executeAsList() }
            .catch { e ->
                logger.error(e) { "Error executing getPasteDataFlow query: ${e.message}" }
                emit(listOf())
            }.flowOn(ioDispatcher)

    override fun getSameHashPasteDataIds(
        hash: String,
        pasteType: Int,
        excludeId: Long,
    ): List<Long> =
        pasteDatabaseQueries
            .getSameHashPasteDataIds(
                hash,
                pasteType.toLong(),
                DateUtils.getOffsetDay(days = -1),
                excludeId,
            ).executeAsList()

    override suspend fun markDeleteByCleanTime(
        cleanTime: Long,
        pasteType: Int?,
    ) {
        batchMarkDelete {
            pasteDatabaseQueries.queryByCleanTime(cleanTime, pasteType?.toLong(), markDeleteBatchNum)
        }
    }

    override suspend fun getActiveCount(): Long =
        withContext(ioDispatcher) {
            pasteDatabaseQueries.getActiveCount().executeAsOne()
        }

    override suspend fun getSize(allOrTagged: Boolean): Long =
        withContext(ioDispatcher) {
            if (allOrTagged) {
                pasteDatabaseQueries.getSize().executeAsOne().SUM ?: 0L
            } else {
                pasteDatabaseQueries.getTaggedSize().executeAsOne().SUM ?: 0L
            }
        }

    override suspend fun getMinPasteDataCreateTime(): Long? =
        withContext(ioDispatcher) {
            pasteDatabaseQueries.getMinCreateTime().executeAsOneOrNull()?.MIN
        }

    override suspend fun updateCreateTime(id: Long): Unit =
        withContext(ioDispatcher) {
            pasteDatabaseQueries.updateCreateTime(id = id, time = DateUtils.nowEpochMilliseconds())
        }

    override suspend fun updatePasteAppearItem(
        id: Long,
        pasteItem: PasteItem,
        pasteSearchContent: String,
        addedSize: Long,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            database
                .transactionWithResult {
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

    override suspend fun updatePasteState(
        id: Long,
        pasteState: Int,
    ) {
        withContext(ioDispatcher) {
            pasteDatabaseQueries.updatePasteDataState(pasteState.toLong(), id)
        }
    }

    override suspend fun getSizeByTimeLessThan(time: Long): Long =
        withContext(ioDispatcher) {
            pasteDatabaseQueries.getSizeByTimeLessThan(time).executeAsOne().SUM ?: 0L
        }

    override suspend fun findCleanTimeByCumulativeSize(targetSize: Long): Long? =
        withContext(ioDispatcher) {
            var cumulativeSize = 0L
            var afterTime = -1L
            var afterId = -1L
            val batchSize = 500L
            while (true) {
                val batch =
                    pasteDatabaseQueries
                        .getOldestUntaggedCreateTimeAndSize(afterTime, afterId, batchSize)
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
                pasteType = pasteType?.toLong(),
                sort = sort,
                tagId = tagId,
                number = limit.toLong(),
                mapper = PasteData::mapper,
            )
        }
    }

    override suspend fun searchPasteData(
        searchTerms: List<String>,
        local: Boolean?,
        pasteType: Int?,
        sort: Boolean,
        tag: Long?,
        limit: Int,
    ): List<PasteData> =
        withContext(ioDispatcher) {
            logExecutionTime(logger, "searchPasteData") {
                logger.info { "Performing search for: $searchTerms" }
                createSearchPasteQuery(searchTerms, local, pasteType, sort, tag, limit).executeAsList()
            }
        }

    override fun searchPasteDataFlow(
        searchTerms: List<String>,
        local: Boolean?,
        pasteType: Int?,
        sort: Boolean,
        tag: Long?,
        limit: Int,
    ): Flow<List<PasteData>> =
        logExecutionTime(logger, "searchPasteData") {
            logger.info { "Performing search for: $searchTerms" }
            createSearchPasteQuery(searchTerms, local, pasteType, sort, tag, limit)
                .asFlow()
                .map { it.executeAsList() }
                .catch { e ->
                    logger.error(e) {
                        "Error executing search query: ${e.message}\n" +
                            "searchTerms=$searchTerms local=$local " +
                            "pasteType=$pasteType sort=$sort"
                    }
                    emit(searchPasteData(listOf(), local, pasteType, sort, tag, limit))
                }.flowOn(ioDispatcher)
        }

    override suspend fun searchBySource(source: String): List<PasteData> =
        withContext(ioDispatcher) {
            pasteDatabaseQueries
                .searchBySource(source, mapper = PasteData::mapper)
                .executeAsList()
        }

    override suspend fun getDistinctSources(): List<String> =
        withContext(ioDispatcher) {
            pasteDatabaseQueries
                .getDistinctSources(appInfo.appInstanceId)
                .executeAsList()
        }

    override suspend fun getPasteResourceInfo(tagged: Boolean?): PasteResourceInfo =
        withContext(ioDispatcher) {
            val taggedPasteIds: Set<Long>? =
                if (tagged != null) {
                    val ids = mutableSetOf<Long>()
                    batchReadPasteData(
                        readPasteDataList = { id, limit ->
                            pasteDatabaseQueries
                                .getBatchPasteData(id, limit, PasteData::mapper)
                                .executeAsList()
                        },
                        dealPasteData = { pasteData ->
                            val hasTags =
                                database.tagDatabaseQueries
                                    .getPasteTags(pasteData.id)
                                    .executeAsList()
                                    .isNotEmpty()
                            if (hasTags) ids.add(pasteData.id)
                        },
                    )
                    ids
                } else {
                    null
                }

            val builder = PasteResourceInfoBuilder()
            val doAdd: (PasteData) -> Unit = { pasteData ->
                val include =
                    when (tagged) {
                        null -> true
                        true -> taggedPasteIds?.contains(pasteData.id) == true
                        false -> taggedPasteIds?.contains(pasteData.id) != true
                    }
                if (include) {
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
                    pasteDatabaseQueries
                        .getBatchPasteData(id, limit, PasteData::mapper)
                        .executeAsList()
                },
                dealPasteData = doAdd,
            )
            builder.build()
        }

    override suspend fun batchReadPasteData(
        batchNum: Long,
        readPasteDataList: suspend (Long, Long) -> List<PasteData>,
        dealPasteData: (PasteData) -> Unit,
    ): Long =
        withContext(ioDispatcher) {
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

    override suspend fun getExportPasteData(
        id: Long,
        limit: Long,
        pasteExportParam: PasteExportParam,
    ): List<PasteData> =
        withContext(ioDispatcher) {
            pasteDatabaseQueries
                .getBatchExportPasteData(
                    id,
                    pasteExportParam.types,
                    pasteExportParam.onlyTagged,
                    limit,
                    PasteData::mapper,
                ).executeAsList()
        }

    override suspend fun getExportNum(pasteExportParam: PasteExportParam): Long =
        withContext(ioDispatcher) {
            pasteDatabaseQueries
                .getExportNum(
                    pasteExportParam.types,
                    pasteExportParam.onlyTagged,
                ).executeAsOne()
        }

    override suspend fun getRecentPasteDataAfterId(
        id: Long,
        limit: Long,
    ): List<PasteData> =
        withContext(ioDispatcher) {
            pasteDatabaseQueries
                .getRecentPasteDataAfterById(
                    appInfo.appInstanceId,
                    id,
                    limit,
                    PasteData::mapper,
                ).executeAsList()
        }

    override suspend fun getRecentPasteDataAfterCreateTime(
        createTime: Long,
        limit: Long,
    ): List<PasteData> =
        withContext(ioDispatcher) {
            pasteDatabaseQueries
                .getRecentPasteDataAfterByCreateTime(
                    appInfo.appInstanceId,
                    createTime,
                    limit,
                    PasteData::mapper,
                ).executeAsList()
        }
}
