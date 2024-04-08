package com.clipevery.clip

import com.clipevery.app.AppInfo
import com.clipevery.dao.clip.ClipDao
import com.clipevery.utils.IDGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

open class DesktopTransferableConsumer(
    private val appInfo: AppInfo,
    private val clipDao: ClipDao,
    private val idGenerator: IDGenerator,
    private val itemServices: List<ClipItemService>,
    private val clipPlugins: List<ClipPlugin>,
) : TransferableConsumer {

    private val logger = KotlinLogging.logger {}

    private val clipItemServiceMap: Map<String, ClipItemService> =
        itemServices.flatMap { service ->
            service.getIdentifiers().map { it to service }
        }.toMap()

    private fun createDataFlavorMap(transferable: Transferable): LinkedHashMap<String, MutableList<DataFlavor>>  {
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
        isRemote: Boolean,
    ) {
        val clipId = idGenerator.nextID()

        val dataFlavorMap: Map<String, List<DataFlavor>> = createDataFlavorMap(transferable)

        dataFlavorMap[LocalOnlyFlavor.humanPresentableName]?.let {
            logger.info { "Ignoring local only flavor" }
            return
        }

        val clipCollector = ClipCollector(dataFlavorMap.size, appInfo, clipDao, clipPlugins)

        try {
            preCollect(clipId, dataFlavorMap, transferable, clipCollector)
            clipCollector.createPreClipData(clipId, isRemote = isRemote)?.let {
                updateClipData(clipId, dataFlavorMap, transferable, clipCollector)
                clipCollector.completeCollect(it)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to consume transferable" }
        }
    }

    private fun preCollect(
        clipId: Long,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        clipCollector: ClipCollector,
    ) {
        var itemIndex = 0
        for (entry in dataFlavorMap) {
            val identifier = entry.key
            val flavors = entry.value
            logger.info { "itemIndex: $itemIndex Transferable flavor: $identifier" }
            for (flavor in flavors) {
                if (clipItemServiceMap[identifier]?.let { clipItemService ->
                        if (clipCollector.needPreCollectionItem(itemIndex, clipItemService::class)) {
                            clipItemService.createPreClipItem(clipId, itemIndex, identifier, transferable, clipCollector)
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

    private fun updateClipData(
        clipId: Long,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        clipCollector: ClipCollector,
    ) {
        var itemIndex = 0
        for (entry in dataFlavorMap) {
            val identifier = entry.key
            val flavors = entry.value
            for (flavor in flavors) {
                if (clipItemServiceMap[identifier]?.let { clipItemService ->
                        if (clipCollector.needUpdateCollectItem(itemIndex, clipItemService::class)) {
                            clipItemService.loadRepresentation(clipId, itemIndex, flavor, dataFlavorMap, transferable, clipCollector)
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
