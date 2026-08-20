package com.crosspaste.db.paste

import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteExportParam
import com.crosspaste.paste.item.PasteItem
import kotlinx.coroutines.flow.Flow

interface PasteDao : SearchPasteData {

    fun getNoDeletePasteDataBlock(id: Long): PasteData?

    fun getNoDeletePasteDataFlow(id: Long): Flow<PasteData?>

    suspend fun getNoDeletePasteData(id: Long): PasteData?

    suspend fun filterExistingIds(ids: List<Long>): List<Long>

    suspend fun getLoadingPasteData(id: Long): PasteData?

    fun getLoadedPasteDataBlock(id: Long): PasteData?

    suspend fun getLatestLoadedPasteData(): PasteData?

    suspend fun getDeletePasteData(id: Long): PasteData?

    suspend fun createPasteData(
        pasteData: PasteData,
        pasteState: Int? = null,
    ): Long

    suspend fun updateFilePath(pasteData: PasteData)

    suspend fun markAllDeleteExceptTagged(): Result<Unit>

    suspend fun markDeletePasteData(id: Long): Result<Unit>

    suspend fun cutPasteData(
        id: Long,
        delayMillis: Long,
    )

    suspend fun deletePasteData(id: Long)

    fun getPasteDataFlow(limit: Long): Flow<List<PasteData>>

    fun getSameHashPasteDataIds(
        hash: String,
        pasteType: Int,
        excludeId: Long,
    ): List<Long>

    /**
     * Returns the id of a LOADED, locally collected record with the same hash
     * and paste type created inside ([minCreateTime], [maxCreateTime]), or null.
     * Used for keep-first dedup of duplicate records produced by a single
     * clipboard operation (e.g. Windows Snipping Tool writes twice per capture);
     * the caller owns the dedup window policy and passes it as an explicit
     * createTime range.
     */
    fun getRecentSameHashLocalPasteId(
        hash: String,
        pasteType: Int,
        minCreateTime: Long,
        maxCreateTime: Long,
        excludeId: Long,
    ): Long?

    suspend fun markDeleteByCleanTime(
        cleanTime: Long,
        pasteType: Int? = null,
    )

    suspend fun getActiveCount(): Long

    suspend fun getSize(allOrTagged: Boolean = false): Long

    suspend fun getMinPasteDataCreateTime(): Long?

    suspend fun updateCreateTime(id: Long)

    /**
     * Atomically replaces the appear item AND the collection (derived
     * clipboard flavors) of a LOADED row. [expectedHash] is the content hash
     * supplied by the editor, while [expectedPasteData] supplies the complete
     * old database values used to reject a race after the server-side read.
     * Returns false on either conflict, deletion, or a still-loading row.
     */
    suspend fun updatePasteContent(
        expectedPasteData: PasteData,
        pasteItem: PasteItem,
        pasteCollection: PasteCollection,
        pasteSearchContent: String,
        addedSize: Long,
        expectedHash: String,
    ): Boolean

    /**
     * Updates one appear item only while its complete serialized old value is
     * still the one in [expectedPasteData]. This is intentionally stricter
     * than a hash guard because metadata changes need not change an item hash.
     */
    suspend fun updatePasteAppearItemIfUnchanged(
        expectedPasteData: PasteData,
        pasteItem: PasteItem,
        pasteSearchContent: String,
        addedSize: Long,
    ): Boolean

    suspend fun updatePasteState(
        id: Long,
        pasteState: Int,
    )

    suspend fun getSizeByTimeLessThan(time: Long): Long

    suspend fun findCleanTimeByCumulativeSize(targetSize: Long): Long?

    suspend fun getDistinctSources(): List<String>

    suspend fun getPasteResourceInfo(tagged: Boolean? = null): PasteResourceInfo

    suspend fun batchReadPasteData(
        batchNum: Long = 1000L,
        readPasteDataList: suspend (Long, Long) -> List<PasteData>,
        dealPasteData: (PasteData) -> Unit,
    ): Long

    suspend fun getExportPasteData(
        id: Long,
        limit: Long,
        pasteExportParam: PasteExportParam,
    ): List<PasteData>

    suspend fun getExportNum(pasteExportParam: PasteExportParam): Long

    suspend fun getRecentPasteDataByAppInstanceId(limit: Long): List<PasteData>

    suspend fun getRecentPasteDataAfterCreateTime(
        createTime: Long,
        limit: Long,
    ): List<PasteData>

    suspend fun getMaxCreateTimeByRemoteAppInstanceId(): Map<String, Long>

    suspend fun getPastePullCursorMaxCreateTimes(): Map<String, Long>

    suspend fun upsertPastePullCursorMaxCreateTime(
        appInstanceId: String,
        maxCreateTime: Long,
    )

    suspend fun deletePastePullCursor(appInstanceId: String)
}
