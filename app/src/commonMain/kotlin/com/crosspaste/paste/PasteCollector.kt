package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.db.paste.PasteCollection
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteState
import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.plugin.type.PasteTypePlugin
import com.crosspaste.utils.LoggerExtension.logSuspendExecutionTime
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.collections.set
import kotlin.reflect.KClass

class PasteCollector(
    itemCount: Int,
    private val appInfo: AppInfo,
    private val pasteDao: PasteDao,
) {

    private val logger = KotlinLogging.logger {}

    private val preCollectors: Array<MutableMap<KClass<out PasteTypePlugin>, PasteItem>> =
        Array(itemCount) { mutableMapOf() }

    private val updateCollectors: Array<MutableSet<KClass<out PasteTypePlugin>>> =
        Array(itemCount) { mutableSetOf() }

    private val updateErrors: Array<Throwable?> = Array(itemCount) { null }

    private var existError = false

    fun needPreCollectionItem(
        itemIndex: Int,
        kclass: KClass<out PasteTypePlugin>,
    ): Boolean = !preCollectors[itemIndex].contains(kclass)

    fun needUpdateCollectItem(
        itemIndex: Int,
        kclass: KClass<out PasteTypePlugin>,
    ): Boolean = !updateCollectors[itemIndex].contains(kclass)

    fun preCollectItem(
        itemIndex: Int,
        kclass: KClass<out PasteTypePlugin>,
        pasteItem: PasteItem,
    ) {
        preCollectors[itemIndex][kclass] = pasteItem
    }

    fun updateCollectItem(
        itemIndex: Int,
        kclass: KClass<out PasteTypePlugin>,
        update: (PasteItem) -> PasteItem,
    ) {
        preCollectors[itemIndex][kclass]?.let {
            updateCollectors[itemIndex].add(kclass)
            preCollectors[itemIndex][kclass] = update(it)
        }
    }

    fun collectError(
        pasteId: Long,
        itemIndex: Int,
        error: Throwable,
    ) {
        logger.error(error) { "Failed to collect item $itemIndex of pasteId $pasteId" }
        updateErrors[itemIndex] = error
        existError = true
    }

    suspend fun createPrePasteData(
        source: String?,
        remote: Boolean,
    ): Long? =
        logSuspendExecutionTime(logger, "createPrePasteData") {
            val collector = preCollectors.filter { it.isNotEmpty() }
            if (collector.isEmpty()) {
                null
            } else {
                val pasteItems: List<PasteItem> = preCollectors.flatMap { it.values }

                val pasteCollection = PasteCollection(pasteItems)

                val pasteData =
                    PasteData(
                        appInstanceId = appInfo.appInstanceId,
                        pasteCollection = pasteCollection,
                        pasteType = PasteType.INVALID_TYPE.type,
                        source = source,
                        size = 0L,
                        hash = "",
                        pasteState = PasteState.LOADING,
                        remote = remote,
                    )

                pasteDao.createPasteData(pasteData)
            }
        }

    suspend fun completeCollect(id: Long) {
        logSuspendExecutionTime(logger, "completeCollect") {
            if (preCollectors.isEmpty() || (existError && updateErrors.all { it != null })) {
                markDeletePasteData(id)
            } else {
                runCatching {
                    val pasteItems = preCollectors.flatMap { it.values }
                    pasteDao.releaseLocalPasteData(id, pasteItems)
                }.onFailure { e ->
                    logger.error(e) { "Failed to complete paste $id" }
                    markDeletePasteData(id)
                }
            }
        }
    }

    private suspend fun markDeletePasteData(id: Long) {
        runCatching {
            pasteDao.markDeletePasteData(id)
        }.onFailure { e ->
            logger.error(e) { "Failed to mark delete paste $id" }
        }
    }
}
