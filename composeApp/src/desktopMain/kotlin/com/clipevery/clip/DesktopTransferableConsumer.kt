package com.clipevery.clip

import com.clipevery.app.AppInfo
import com.clipevery.dao.clip.ClipDao
import com.clipevery.utils.IDGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

open class DesktopTransferableConsumer(private val appInfo: AppInfo,
                                       private val clipDao: ClipDao,
                                       private val idGenerator: IDGenerator,
                                       private val itemServices: List<ClipItemService>,
                                       private val clipPlugins: List<ClipPlugin>): TransferableConsumer {

    private val logger = KotlinLogging.logger {}

    private val clipItemServiceMap: Map<String, ClipItemService> = itemServices.flatMap { service ->
        service.getIdentifiers().map { it to service }
    }.toMap()

    private fun createDataFlavorMap(transferable: Transferable): LinkedHashMap<String, MutableList<DataFlavor>>{
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

    override fun consume(transferable: Transferable) {
        val clipId = idGenerator.nextID()

        val dataFlavorMap: Map<String, List<DataFlavor>> = createDataFlavorMap(transferable)

        val clipCollector = ClipCollector(dataFlavorMap.size, appInfo, clipDao, clipPlugins)

        try {
            var itemIndex = 0
            for (entry in dataFlavorMap) {
                val identifier = entry.key
                val flavors = entry.value
                logger.info { "itemIndex: $itemIndex Transferable flavor: $identifier" }
                for (flavor in flavors) {
                    clipItemServiceMap[identifier]?.let { clipItemService ->
                        if (clipCollector.needCollectionItem(itemIndex, clipItemService::class)) {
                            clipItemService.createClipItem(clipId, itemIndex, flavor, dataFlavorMap, transferable, clipCollector)
                        }
                    }
                }
                itemIndex ++
            }
            clipCollector.completeCollect(clipId)
        } catch (e: Exception) {
            logger.error(e) { "Failed to consume transferable" }
        }
    }
}