package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.plugin.type.PasteTypePlugin
import com.crosspaste.utils.LoggerExtension.logSuspendExecutionTime
import io.github.oshai.kotlinlogging.KotlinLogging

class DesktopTransferableConsumer(
    private val appInfo: AppInfo,
    private val pasteDao: PasteDao,
    pasteTypePlugins: List<PasteTypePlugin>,
) : TransferableConsumer {

    override val logger = KotlinLogging.logger {}

    private val pasteTypePluginMap: Map<String, PasteTypePlugin> =
        pasteTypePlugins.flatMap { pasteTypePlugin ->
            pasteTypePlugin.getIdentifiers().map { it to pasteTypePlugin }
        }.toMap()

    private fun createDataFlavorMap(pasteTransferable: PasteTransferable): Map<String, List<PasteDataFlavor>> {
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
            val dataFlavorMap: Map<String, List<PasteDataFlavor>> = createDataFlavorMap(pasteTransferable)

            dataFlavorMap[LocalOnlyFlavor.humanPresentableName]?.let {
                logger.info { "Ignoring local only flavor" }
                return@logSuspendExecutionTime
            }

            val pasteCollector = PasteCollector(dataFlavorMap.size, appInfo, pasteDao)

            runCatching {
                preCollect(dataFlavorMap, pasteTransferable, pasteCollector)
                pasteCollector.createPrePasteData(source, remote = remote)?.let { id ->
                    updatePasteData(id, dataFlavorMap, pasteTransferable, pasteCollector)
                    pasteCollector.completeCollect(id)
                }
            }.onFailure { e ->
                logger.error(e) { "Failed to consume transferable" }
            }
        }
    }

    override fun getPlugin(identity: String): PasteTypePlugin? {
        return pasteTypePluginMap[identity]
    }
}
