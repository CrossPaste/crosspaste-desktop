package com.crosspaste.paste

import com.crosspaste.Database
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemProperties
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.bindItem
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndex
import com.crosspaste.presist.buildFilesIndexForReceive
import com.crosspaste.sync.FilePullService
import com.crosspaste.task.TaskBuilder
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.ExperimentalTime

data class PushPrepareResult(
    val pasteId: Long,
    val filesIndex: FilesIndex,
    val needIcon: Boolean,
    val chunkSize: Long = FilePullService.CHUNK_SIZE,
) {
    val chunkCount: Int get() = filesIndex.getChunkCount()
}

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
        targetAppInstanceIds: Set<String>?,
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
                        pasteAppearItem = firstItem.toStoredJson(),
                        pasteCollection = PasteCollection(remainingItems).toStoredJson(),
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
                    // Skip syncing items already from another device (would cause sync loops).
                    // Empty target set = every peer filtered out (e.g. Apple Universal Clipboard
                    // items skip Apple peers).
                    val hasValidSyncTargets = targetAppInstanceIds == null || targetAppInstanceIds.isNotEmpty()
                    if (!pasteData.remote && hasValidSyncTargets) {
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

    /**
     * Shared file-paste landing pad for both directions of remote receive:
     * pull ([releaseRemotePasteData]) and push ([releaseRemotePasteDataForPush]).
     *
     * Creates the LOADING row, computes `syncToDownload`, rebinds the items to the
     * new PasteCoordinate, and writes the storage paths. Returns the bound
     * PasteData (with the freshly-assigned id) or null when the input has no
     * [PasteFiles] item — defensive guard, file-type pastes should always carry one.
     */
    private suspend fun bindFilePasteForReceive(pasteData: PasteData): PasteData? {
        val pasteFiles = pasteData.getPasteItem(PasteFiles::class) ?: return null

        val id = pasteDao.createPasteData(pasteData, PasteState.LOADING)

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
        val newPasteAppearItem =
            pasteData.pasteAppearItem?.bindItem(pasteCoordinate, syncToDownload)
        val newPasteCollection =
            pasteData.pasteCollection.bindItems(pasteCoordinate, syncToDownload)
        val newPasteData =
            pasteData.copy(
                id = id,
                pasteAppearItem = newPasteAppearItem,
                pasteCollection = newPasteCollection,
            )

        pasteDao.updateFilePath(newPasteData)
        return newPasteData
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
                    fileUtils.existFile(userDataPathProvider.resolveIconPath(pasteData.appInstanceId, it))
                }

            taskSubmitter.submit {
                val id: Long =
                    if (!isFileType) {
                        val newId = pasteDao.createPasteData(pasteData, PasteState.LOADED)
                        markDeleteSameHash(newId, pasteData.pasteType, pasteData.hash)
                        addRenderingTask(newId, pasteData.getType())
                        tryWritePasteboard(pasteData)
                        newId
                    } else {
                        val newPasteData =
                            bindFilePasteForReceive(pasteData) ?: run {
                                logger.warn {
                                    "File-type paste from ${pasteData.appInstanceId} has no PasteFiles item, skipping"
                                }
                                return@submit
                            }
                        addPullFileTask(newPasteData.id, remotePasteDataId)
                        addRelaySyncTask(newPasteData.id, newPasteData.appInstanceId)
                        newPasteData.id
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

    /**
     * Push-mode counterpart of [releaseRemotePasteData] for file-type pastes.
     *
     * Synchronously creates a LOADING PasteData, binds destination paths and builds
     * the FilesIndex describing the slots that incoming chunk uploads will fill. The
     * caller is expected to attach the FilesIndex to a push session so chunk lookups
     * go through the session. Side effects mirror the file-type branch of
     * [releaseRemotePasteData]: a relay-sync task is scheduled so other peers will
     * eventually receive the paste, and a pull-icon task is queued when the source
     * app icon isn't already cached locally. Returns null when the paste is not a
     * file type or storage binding fails — callers should treat that as a rejection.
     */
    suspend fun releaseRemotePasteDataForPush(pasteData: PasteData): PushPrepareResult? =
        withContext(ioDispatcher) {
            if (!pasteData.isFileType()) {
                logger.warn { "releaseRemotePasteDataForPush: paste is not a file type (${pasteData.getType()})" }
                return@withContext null
            }
            runCatching {
                val existIconFile: Boolean? =
                    pasteData.source?.let {
                        fileUtils.existFile(userDataPathProvider.resolveIconPath(pasteData.appInstanceId, it))
                    }

                val newPasteData =
                    bindFilePasteForReceive(pasteData) ?: run {
                        logger.warn { "releaseRemotePasteDataForPush: paste has no PasteFiles item" }
                        return@runCatching null
                    }
                val id = newPasteData.id

                val filesIndex =
                    buildFilesIndexForReceive(newPasteData, userDataPathProvider, FilePullService.CHUNK_SIZE)
                if (filesIndex.getChunkCount() <= 0) {
                    logger.warn { "releaseRemotePasteDataForPush: empty filesIndex for pasteId=$id" }
                    pasteDao.markDeletePasteData(id)
                    return@runCatching null
                }

                taskSubmitter.submit {
                    addRelaySyncTask(id, newPasteData.appInstanceId)
                    existIconFile?.let { addPullIconTask(id, it) }
                }

                PushPrepareResult(
                    pasteId = id,
                    filesIndex = filesIndex,
                    needIcon = existIconFile == false,
                )
            }.onFailure { e ->
                logger.error(e) { "releaseRemotePasteDataForPush failed" }
            }.getOrNull()
        }
}
