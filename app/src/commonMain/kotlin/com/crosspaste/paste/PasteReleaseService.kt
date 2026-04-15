package com.crosspaste.paste

import com.crosspaste.Database
import com.crosspaste.app.AppFileType
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemProperties
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.bindItem
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.task.TaskBuilder
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.ExperimentalTime

class PasteReleaseService(
    private val commonConfigManager: CommonConfigManager,
    private val currentPaste: CurrentPaste,
    private val database: Database,
    private val pasteDao: PasteDao,
    private val pasteItemReader: PasteItemReader,
    private val pasteProcessPlugins: List<PasteProcessPlugin>,
    private val searchContentService: SearchContentService,
    private val taskSubmitter: TaskSubmitter,
    private val userDataPathProvider: UserDataPathProvider,
) {

    companion object {
        private val fileUtils = getFileUtils()
    }

    private val logger = KotlinLogging.logger {}

    @OptIn(ExperimentalTime::class)
    private fun TaskBuilder.markDeleteSameHash(
        newPasteDataId: Long,
        newPasteDataType: Int,
        newPasteDataHash: String,
    ) {
        if (newPasteDataHash.isEmpty()) {
            return
        }

        val idList =
            pasteDao.getSameHashPasteDataIds(
                newPasteDataHash,
                newPasteDataType,
                newPasteDataId,
            )

        database.transaction {
            database.pasteDatabaseQueries.markDeletePasteData(idList)
            addDeletePasteTasks(idList)
        }
    }

    suspend fun releaseLocalPasteData(
        id: Long,
        pasteItems: List<PasteItem>,
        targetAppInstanceIds: Set<String>,
    ) = withContext(ioDispatcher) {
        pasteDao.getLoadingPasteData(id)?.let { pasteData ->
            var pasteAppearItems = pasteItems
            for (pastePlugin in pasteProcessPlugins) {
                pasteAppearItems =
                    pastePlugin.process(
                        pasteData.getPasteCoordinate(),
                        pasteAppearItems,
                        pasteData.source,
                    )
            }

            if (pasteAppearItems.isEmpty()) {
                pasteDao.markDeletePasteData(id)
                return@let
            }

            val size = pasteAppearItems.sumOf { it.size }
            val maxFileSize =
                pasteAppearItems
                    .filter { it is PasteFiles }
                    .maxByOrNull { it.size }
                    ?.size ?: 0
            // first item as pasteAppearItem
            // remaining items as pasteContent
            val firstItem: PasteItem = pasteAppearItems.first()
            val remainingItems: List<PasteItem> = pasteAppearItems.drop(1)

            val hash = firstItem.hash
            val pasteType = firstItem.getPasteType()

            val change =
                database.transactionWithResult {
                    database.pasteDatabaseQueries.updatePasteDataToLoaded(
                        pasteAppearItem = firstItem.toJson(),
                        pasteCollection = PasteCollection(remainingItems).toJson(),
                        pasteType = pasteType.type.toLong(),
                        pasteSearchContent =
                            searchContentService.createSearchContent(
                                pasteData.source,
                                pasteItemReader.getSearchContent(firstItem),
                            ),
                        size = size,
                        hash = hash,
                        id = id,
                    )
                    database.pasteDatabaseQueries.change().executeAsOne() > 0
                }

            if (change) {
                currentPaste.setPasteId(id)
                taskSubmitter.submit {
                    addRenderingTask(id, pasteType)
                    // Skip syncing for remote clipboard data (e.g., Apple Universal Clipboard / Handoff).
                    // These items are already from another device, so re-syncing them would cause
                    // unnecessary duplication or sync loops.
                    if (!pasteData.remote) {
                        addSyncTask(id, maxFileSize, pasteData.appInstanceId, targetAppInstanceIds)
                    }

                    if (pasteType.isFile() || pasteType.isImage()) {
                        if ((firstItem as PasteFiles).isRefFiles()) {
                            markDeleteSameHash(id, pasteType.type, hash)
                        }
                    } else {
                        markDeleteSameHash(id, pasteType.type, hash)
                    }
                }
            }
        }
    }

    suspend fun releaseRemotePasteData(
        pasteData: PasteData,
        tryWritePasteboard: (PasteData) -> Unit,
    ): Result<Unit> {
        return runCatching {
            val remotePasteDataId = pasteData.id
            val isFileType = pasteData.isFileType()
            val existIconFile: Boolean? =
                pasteData.source?.let {
                    fileUtils.existFile(userDataPathProvider.resolve("$it.png", AppFileType.ICON))
                }

            val pasteState =
                if (isFileType) {
                    PasteState.LOADING
                } else {
                    PasteState.LOADED
                }

            val id = pasteDao.createPasteData(pasteData, pasteState)

            taskSubmitter.submit {
                if (!isFileType) {
                    markDeleteSameHash(id, pasteData.pasteType, pasteData.hash)
                    addRenderingTask(id, pasteData.getType())
                    tryWritePasteboard(pasteData)
                } else {
                    val pasteFiles = pasteData.getPasteItem(PasteFiles::class)

                    if (pasteFiles == null) {
                        logger.warn { "File-type paste $id has no PasteFiles item, skipping" }
                        return@submit
                    }

                    val fileSize = pasteFiles.size
                    val maxBackupFileSize =
                        fileUtils.bytesSize(
                            commonConfigManager.getCurrentConfig().maxBackupFileSize,
                        )

                    val syncToDownload =
                        fileSize > maxBackupFileSize ||
                            pasteData.pasteAppearItem
                                ?.extraInfo
                                ?.get(PasteItemProperties.SYNC_TO_DOWNLOAD)
                                ?.jsonPrimitive
                                ?.booleanOrNull == true

                    val pasteCoordinate = pasteData.getPasteCoordinate(id)
                    val pasteAppearItem = pasteData.pasteAppearItem
                    val pasteCollection = pasteData.pasteCollection

                    val newPasteAppearItem = pasteAppearItem?.bindItem(pasteCoordinate, syncToDownload)
                    val newPasteCollection = pasteCollection.bindItems(pasteCoordinate, syncToDownload)

                    val newPasteData =
                        pasteData.copy(
                            id = id,
                            pasteAppearItem = newPasteAppearItem,
                            pasteCollection = newPasteCollection,
                        )

                    pasteDao.updateFilePath(newPasteData)

                    addPullFileTask(id, remotePasteDataId)
                    addRelaySyncTask(id, newPasteData.appInstanceId)
                }

                existIconFile?.let {
                    addPullIconTask(id, it)
                }
            }
        }.onFailure { e ->
            logger.error(e) { "Release remote paste data failed" }
        }
    }

    suspend fun releaseRemotePasteDataWithFile(
        id: Long,
        tryWritePasteboard: (PasteData) -> Unit,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                taskSubmitter.submit {
                    database
                        .transactionWithResult {
                            database.pasteDatabaseQueries.updatePasteDataState(PasteState.LOADED.toLong(), id)
                            pasteDao.getNoDeletePasteDataBlock(id)
                        }?.let {
                            markDeleteSameHash(id, it.pasteType, it.hash)
                            addRelaySyncTask(id, it.appInstanceId)
                            tryWritePasteboard(it)
                        }
                }
            }.onFailure { e ->
                logger.error(e) { "Release remote paste data with file failed" }
            }
        }
}
