package com.crosspaste.paste

import com.crosspaste.paste.plugin.type.PasteTypePlugin
import com.crosspaste.utils.LoggerExtension.logExecutionTime
import io.github.oshai.kotlinlogging.KLogger
import kotlin.collections.component1

interface TransferableConsumer {

    val logger: KLogger

    suspend fun consume(
        pasteTransferable: PasteTransferable,
        source: String?,
        remote: Boolean,
    )

    fun getPlugin(identity: String): PasteTypePlugin?

    fun preCollect(
        pasteId: Long,
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        logExecutionTime(logger, "preCollect") {
            for ((itemIndex, entry) in dataFlavorMap.entries.withIndex()) {
                val identity = entry.key
                logger.info { "Processing item $itemIndex with flavor: $identity" }
                val plugin = getPlugin(identity) ?: continue
                if (pasteCollector.needPreCollectionItem(itemIndex, plugin::class)) {
                    plugin.createPrePasteItem(
                        pasteId = pasteId,
                        itemIndex = itemIndex,
                        identifier = identity,
                        pasteTransferable = pasteTransferable,
                        pasteCollector = pasteCollector,
                    )
                }
            }
        }
    }

    fun updatePasteData(
        pasteId: Long,
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        logExecutionTime(logger, "updatePasteData") {
            for ((itemIndex, entry) in dataFlavorMap.entries.withIndex()) {
                val (identity, flavors) = entry
                val plugin = getPlugin(identity) ?: continue
                for (flavor in flavors) {
                    if (pasteCollector.needUpdateCollectItem(itemIndex, plugin::class)) {
                        plugin.loadRepresentation(
                            pasteId,
                            itemIndex,
                            flavor,
                            dataFlavorMap,
                            pasteTransferable,
                            pasteCollector,
                        )
                    } else {
                        break
                    }
                }
            }
        }
    }
}
