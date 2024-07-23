package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.paste.plugin.type.PasteTypePlugin
import com.crosspaste.utils.IDGenerator
import com.crosspaste.utils.LoggerExtension.logExecutionTime
import com.crosspaste.utils.LoggerExtension.logSuspendExecutionTime
import io.github.oshai.kotlinlogging.KotlinLogging

open class DesktopTransferableConsumer(
    private val appInfo: AppInfo,
    private val pasteDao: PasteDao,
    private val idGenerator: IDGenerator,
    private val pasteProcessPlugins: List<PasteProcessPlugin>,
    pasteTypePlugins: List<PasteTypePlugin>,
) : TransferableConsumer {

    private val logger = KotlinLogging.logger {}

    private val pasteTypePluginMap: Map<String, PasteTypePlugin> =
        pasteTypePlugins.flatMap { pasteTypePlugin ->
            pasteTypePlugin.getIdentifiers().map { it to pasteTypePlugin }
        }.toMap()

    private fun createDataFlavorMap(pasteTransferable: PasteTransferable): LinkedHashMap<String, MutableList<PasteDataFlavor>> {
        val dataFlavorMap = LinkedHashMap<String, MutableList<PasteDataFlavor>>()
        pasteTransferable as DesktopReadTransferable
        for (flavor in pasteTransferable.transferable.transferDataFlavors) {
            val humanPresentableName = flavor.humanPresentableName
            if (!dataFlavorMap.containsKey(humanPresentableName)) {
                dataFlavorMap[humanPresentableName] = mutableListOf()
            }
            dataFlavorMap[humanPresentableName]?.add(flavor.toPasteDataFlavor())
        }
        return dataFlavorMap
    }

    override suspend fun consume(
        pasteTransferable: PasteTransferable,
        source: String?,
        remote: Boolean,
    ) {
        logSuspendExecutionTime(logger, "consume") {
            val pasteId = idGenerator.nextID()

            val dataFlavorMap: Map<String, List<PasteDataFlavor>> = createDataFlavorMap(pasteTransferable)

            dataFlavorMap[LocalOnlyFlavor.humanPresentableName]?.let {
                logger.info { "Ignoring local only flavor" }
                return@logSuspendExecutionTime
            }

            val pasteCollector = PasteCollector(dataFlavorMap.size, appInfo, pasteDao, pasteProcessPlugins)

            try {
                preCollect(pasteId, dataFlavorMap, pasteTransferable, pasteCollector)
                pasteCollector.createPrePasteData(pasteId, source, remote = remote)?.let {
                    updatePasteData(pasteId, dataFlavorMap, pasteTransferable, pasteCollector)
                    pasteCollector.completeCollect(it)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to consume transferable" }
            }
        }
    }

    private fun preCollect(
        pasteId: Long,
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        logExecutionTime(logger, "preCollect") {
            var itemIndex = 0
            for (entry in dataFlavorMap) {
                val identifier = entry.key
                val flavors = entry.value
                logger.info { "itemIndex: $itemIndex Transferable flavor: $identifier" }
                for (flavor in flavors) {
                    if (pasteTypePluginMap[identifier]?.let { pasteTypePlugin ->
                            if (pasteCollector.needPreCollectionItem(itemIndex, pasteTypePlugin::class)) {
                                pasteTypePlugin.createPrePasteItem(
                                    pasteId,
                                    itemIndex,
                                    identifier,
                                    pasteTransferable,
                                    pasteCollector,
                                )
                                false
                            } else {
                                true
                            }
                        } == true
                    ) {
                        break
                    }
                }
                itemIndex++
            }
        }
    }

    private fun updatePasteData(
        pasteId: Long,
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        logExecutionTime(logger, "updatePasteData") {
            var itemIndex = 0
            for (entry in dataFlavorMap) {
                val identifier = entry.key
                val flavors = entry.value
                for (flavor in flavors) {
                    if (pasteTypePluginMap[identifier]?.let { pasteItemService ->
                            if (pasteCollector.needUpdateCollectItem(itemIndex, pasteItemService::class)) {
                                pasteItemService.loadRepresentation(
                                    pasteId,
                                    itemIndex,
                                    flavor,
                                    dataFlavorMap,
                                    pasteTransferable,
                                    pasteCollector,
                                )
                                false
                            } else {
                                true
                            }
                        } == true
                    ) {
                        break
                    }
                }
                itemIndex++
            }
        }
    }
}
