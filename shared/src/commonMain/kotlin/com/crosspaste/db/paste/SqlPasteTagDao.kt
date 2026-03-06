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

class SqlPasteTagDao(
    private val database: Database,
) : PasteTagDao {

    private val logger = KotlinLogging.logger {}

    private val tagDatabaseQueries = database.tagDatabaseQueries

    override fun getAllTagsFlow(): Flow<List<PasteTag>> =
        tagDatabaseQueries
            .getAllTags(PasteTag::mapper)
            .asFlow()
            .map { it.executeAsList() }
            .catch { e ->
                logger.error(e) { "Error executing getAllTagsFlow query: ${e.message}" }
                emit(listOf())
            }.flowOn(ioDispatcher)

    override suspend fun getMaxSortOrder(): Long =
        withContext(ioDispatcher) {
            tagDatabaseQueries.maxSortOrder().executeAsOne()
        }

    override suspend fun createPasteTag(
        name: String,
        color: Long,
    ): Long =
        withContext(ioDispatcher) {
            database.transactionWithResult {
                val maxSortOrder = tagDatabaseQueries.maxSortOrder().executeAsOne()
                val newSortOrder = maxSortOrder + 1
                tagDatabaseQueries.createTag(name, color, newSortOrder)
                tagDatabaseQueries.getLastId().executeAsOne()
            }
        }

    override suspend fun updatePasteTagName(
        id: Long,
        name: String,
    ) {
        withContext(ioDispatcher) {
            tagDatabaseQueries.updateTagName(name, id)
        }
    }

    override suspend fun updatePasteTagColor(
        id: Long,
        color: Long,
    ) {
        withContext(ioDispatcher) {
            tagDatabaseQueries.updateTagColor(color, id)
        }
    }

    override fun switchPinPasteTagBlock(
        pasteDataId: Long,
        pasteTagId: Long,
    ) {
        database.transaction {
            val pinned = tagDatabaseQueries.isPinnedPasteTag(pasteDataId, pasteTagId).executeAsOne()
            if (pinned) {
                unPinPasteTag(pasteDataId, pasteTagId)
            } else {
                pinPasteTag(pasteDataId, pasteTagId)
            }
        }
    }

    override fun getPasteTagsBlock(pasteDataId: Long): List<Long> =
        tagDatabaseQueries.getPasteTags(pasteDataId).executeAsList()

    override fun deletePasteTagBlock(id: Long) {
        tagDatabaseQueries.deleteTag(id)
    }

    override fun getAllTagsBlock(): List<PasteTag> = tagDatabaseQueries.getAllTags(PasteTag::mapper).executeAsList()

    private fun pinPasteTag(
        pasteDataId: Long,
        pasteTagId: Long,
    ) {
        tagDatabaseQueries.pinPasteTag(pasteDataId, pasteTagId)
    }

    private fun unPinPasteTag(
        pasteDataId: Long,
        pasteTagId: Long,
    ) {
        tagDatabaseQueries.unPinPasteTag(pasteDataId, pasteTagId)
    }
}
