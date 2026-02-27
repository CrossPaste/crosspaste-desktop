package com.crosspaste.db.paste

import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteExportParam
import com.crosspaste.paste.item.PasteItem
import kotlinx.coroutines.flow.Flow

interface PasteDao : SearchPasteData {

    fun getNoDeletePasteDataBlock(id: Long): PasteData?

    fun getNoDeletePasteDataFlow(id: Long): Flow<PasteData?>

    suspend fun getNoDeletePasteData(id: Long): PasteData?

    suspend fun getLoadingPasteData(id: Long): PasteData?

    fun getLoadedPasteDataBlock(id: Long): PasteData?

    suspend fun getLatestLoadedPasteData(): PasteData?

    suspend fun getDeletePasteData(id: Long): PasteData?

    suspend fun setFavorite(
        pasteId: Long,
        favorite: Boolean,
    )

    suspend fun createPasteData(
        pasteData: PasteData,
        pasteState: Int? = null,
    ): Long

    suspend fun updateFilePath(pasteData: PasteData)

    suspend fun markAllDeleteExceptFavorite(): Result<Unit>

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

    suspend fun markDeleteByCleanTime(
        cleanTime: Long,
        pasteType: Int? = null,
    )

    suspend fun getActiveCount(): Long

    suspend fun getSize(allOrFavorite: Boolean = false): Long

    suspend fun getMinPasteDataCreateTime(): Long?

    suspend fun updateCreateTime(id: Long)

    suspend fun updatePasteAppearItem(
        id: Long,
        pasteItem: PasteItem,
        pasteSearchContent: String,
        addedSize: Long = 0L,
    ): Result<Unit>

    suspend fun updatePasteState(
        id: Long,
        pasteState: Int,
    )

    suspend fun getSizeByTimeLessThan(time: Long): Long

    suspend fun findCleanTimeByCumulativeSize(targetSize: Long): Long?

    suspend fun getDistinctSources(): List<String>

    suspend fun getPasteResourceInfo(favorite: Boolean? = null): PasteResourceInfo

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
}
