package com.crosspaste.db.paste

import app.cash.sqldelight.coroutines.asFlow
import com.crosspaste.Database
import com.crosspaste.paste.PasteTag
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PasteTagDao(
    private val database: Database,
) {

    private val logger = KotlinLogging.logger {}

    private val tagDatabaseQueries = database.tagDatabaseQueries

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

    fun getPasteTagsBlock(pasteDataId: Long): List<Long> {
        return tagDatabaseQueries.getPasteTags(pasteDataId).executeAsList()
    }

    fun deletePasteTagBlock(id: Long) {
        tagDatabaseQueries.deleteTag(id)
    }

    private fun pinPasteTag(pasteDataId: Long, pasteTagId: Long) {
        tagDatabaseQueries.pinPasteTag(pasteDataId, pasteTagId)
    }

    private fun unPinPasteTag(pasteDataId: Long, pasteTagId: Long) {
        tagDatabaseQueries.unPinPasteTag(pasteDataId, pasteTagId)
    }
}
