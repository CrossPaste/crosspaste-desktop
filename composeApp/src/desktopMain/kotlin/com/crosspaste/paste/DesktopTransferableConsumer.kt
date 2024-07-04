package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.utils.IDGenerator
import com.crosspaste.utils.LoggerExtension.logExecutionTime
import com.crosspaste.utils.LoggerExtension.logSuspendExecutionTime
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

open class DesktopTransferableConsumer(
    private val appInfo: AppInfo,
    private val pasteDao: PasteDao,
    private val idGenerator: IDGenerator,
    private val itemServices: List<PasteItemService>,
    private val pastePlugins: List<PastePlugin>,
) : TransferableConsumer {

    private val logger = KotlinLogging.logger {}

    private val pasteItemServiceMap: Map<String, PasteItemService> =
        itemServices.flatMap { service ->
            service.getIdentifiers().map { it to service }
        }.toMap()

    private fun createDataFlavorMap(transferable: Transferable): LinkedHashMap<String, MutableList<DataFlavor>> {
        val dataFlavorMap = LinkedHashMap<String, MutableList<DataFlavor>>()

        for (flavor in transferable.transferDataFlavors) {
            val humanPresentableName = flavor.humanPresentableName
            if (!dataFlavorMap.containsKey(humanPresentableName)) {
                dataFlavorMap[humanPresentableName] = mutableListOf()
            }
            dataFlavorMap[humanPresentableName]?.add(flavor)
        }
        return dataFlavorMap
    }

    override suspend fun consume(
        transferable: Transferable,
        source: String?,
        remote: Boolean,
    ) {
        logSuspendExecutionTime(logger, "consume") {
            val pasteId = idGenerator.nextID()

            val dataFlavorMap: Map<String, List<DataFlavor>> = createDataFlavorMap(transferable)

            dataFlavorMap[LocalOnlyFlavor.humanPresentableName]?.let {
                logger.info { "Ignoring local only flavor" }
                return@logSuspendExecutionTime
            }

            val pasteCollector = PasteCollector(dataFlavorMap.size, appInfo, pasteDao, pastePlugins)

            try {
                preCollect(pasteId, dataFlavorMap, transferable, pasteCollector)
                pasteCollector.createPrePasteData(pasteId, source, remote = remote)?.let {
                    updatePasteData(pasteId, dataFlavorMap, transferable, pasteCollector)
                    pasteCollector.completeCollect(it)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to consume transferable" }
            }
        }
    }

    private fun preCollect(
        pasteId: Long,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        pasteCollector: PasteCollector,
    ) {
        logExecutionTime(logger, "preCollect") {
            var itemIndex = 0
            for (entry in dataFlavorMap) {
                val identifier = entry.key
                val flavors = entry.value
                logger.info { "itemIndex: $itemIndex Transferable flavor: $identifier" }
                for (flavor in flavors) {
                    if (pasteItemServiceMap[identifier]?.let { pasteItemService ->
                            if (pasteCollector.needPreCollectionItem(itemIndex, pasteItemService::class)) {
                                pasteItemService.createPrePasteItem(
                                    pasteId,
                                    itemIndex,
                                    identifier,
                                    transferable,
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
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        pasteCollector: PasteCollector,
    ) {
        logExecutionTime(logger, "updatePasteData") {
            var itemIndex = 0
            for (entry in dataFlavorMap) {
                val identifier = entry.key
                val flavors = entry.value
                for (flavor in flavors) {
                    if (pasteItemServiceMap[identifier]?.let { pasteItemService ->
                            if (pasteCollector.needUpdateCollectItem(itemIndex, pasteItemService::class)) {
                                pasteItemService.loadRepresentation(
                                    pasteId,
                                    itemIndex,
                                    flavor,
                                    dataFlavorMap,
                                    transferable,
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
