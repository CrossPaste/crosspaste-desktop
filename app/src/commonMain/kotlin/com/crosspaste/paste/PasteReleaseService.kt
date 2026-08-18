package com.crosspaste.paste

import com.crosspaste.Database
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemProperties
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.bindItem
import com.crosspaste.paste.plugin.process.DiscardOversizedNonFilePlugin
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndex
import com.crosspaste.presist.buildFilesIndexForReceive
import com.crosspaste.presist.validateFileTransferMetadata
import com.crosspaste.sync.FilePullService
import com.crosspaste.sync.PastePullCursorManager
import com.crosspaste.task.TaskBuilder
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds
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
    private val notificationManager: NotificationManager,
    private val pasteDao: PasteDao,
    private val pasteItemReader: PasteItemReader,
    private val pasteProcessPlugins: List<PasteProcessPlugin>,
    private val pastePullCursorManager: PastePullCursorManager,
    private val searchContentService: SearchContentService,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val taskSubmitter: TaskSubmitter,
    private val userDataPathProvider: UserDataPathProvider,
) {

    companion object {
        private val fileUtils = getFileUtils()
        private const val DISCARD_PUSH_PREPARED_ATTEMPTS = 3
        private val DISCARD_PUSH_PREPARED_RETRY_DELAY = 50.milliseconds
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
                    runCatching {
                        pastePlugin.process(
                            pasteData.getPasteCoordinate(),
                            pasteAppearItems,
                            pasteData.source,
                        )
                    }.getOrElse { e ->
                        // A failing plugin must not leave the row stuck in loading state;
                        // skip its transformation and continue with the current items.
                        logger.warn(e) {
                            "Paste process plugin ${pastePlugin::class.simpleName} failed, id=$id"
                        }
                        pasteAppearItems
                    }
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

            if (discardDuplicateLocalImage(pasteData, firstItem, remainingItems, size, hash, pasteType, id)) {
                return@let
            }

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
     * Keep-first dedup for locally collected non-ref images: a single clipboard
     * operation can fire multiple clipboard events (Windows Snipping Tool writes
     * twice per capture), each collected into an identical record. Non-ref images
     * intentionally bypass [markDeleteSameHash] (the #2693 resource-lifecycle
     * guard), so an identical local image released within
     * [com.crosspaste.db.paste.SqlPasteDao.RECENT_SAME_HASH_WINDOW] identifies
     * this record as that duplicate: keep the first record, discard this one.
     *
     * The final items (with the real file paths) must be persisted before the row
     * is marked deleted — at this point the row still holds the pre-collect
     * placeholder, and deleting it as-is would orphan the just-written files.
     * Only a delete task is created, never sync or rendering tasks, so the
     * discarded record never has in-flight consumers and clearing its files
     * cannot race anything — which is what keeps the #2693 guard intact.
     *
     * Returns true when the record was discarded as a duplicate.
     */
    private suspend fun discardDuplicateLocalImage(
        pasteData: PasteData,
        firstItem: PasteItem,
        remainingItems: List<PasteItem>,
        size: Long,
        hash: String,
        pasteType: PasteType,
        id: Long,
    ): Boolean {
        if (pasteData.remote ||
            !pasteType.isImage() ||
            firstItem !is PasteFiles ||
            firstItem.isRefFiles() ||
            hash.isEmpty()
        ) {
            return false
        }

        val keptId = pasteDao.getRecentSameHashLocalPasteId(hash, pasteType.type, id) ?: return false

        logger.info { "Discarding duplicate local image paste id=$id, keeping recent record id=$keptId" }
        taskSubmitter.submit {
            database.transaction {
                database.pasteDatabaseQueries.updatePasteDataToLoaded(
                    pasteAppearItem = firstItem.toStoredJson(),
                    pasteCollection = PasteCollection(remainingItems).toStoredJson(),
                    pasteType = pasteType.type.toLong(),
                    pasteSearchContent = null,
                    size = size,
                    hash = hash,
                    id = id,
                )
                database.pasteDatabaseQueries.markDeletePasteData(listOf(id))
                addDeletePasteTasks(listOf(id))
            }
        }
        return true
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

        userDataPathProvider.validateReceivePaths(pasteData.appInstanceId, pasteFiles)

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

    /**
     * Remote pastes bypass the plugin pipeline (they are persisted as-is), so the
     * non-file size limit is enforced here. The whole paste is skipped instead of
     * degrading flavors: the remote PasteData's hash/pasteType/searchContent are
     * already assembled, and dropping individual items would break the
     * hash-content consistency that markDeleteSameHash dedup relies on. The
     * sender keeps the full data, so nothing is lost.
     */
    private suspend fun discardOversizedRemoteNonFilePaste(pasteData: PasteData): Boolean {
        val maxSize = fileUtils.bytesSize(commonConfigManager.getCurrentConfig().maxNonFilePasteSize)
        val oversized =
            pasteData.getPasteAppearItems().any {
                DiscardOversizedNonFilePlugin.isNonFilePasteType(it.getPasteType()) && it.size > maxSize
            }
        if (!oversized) {
            return false
        }
        logger.info {
            "Discard oversized remote non-file paste from ${pasteData.appInstanceId}: " +
                "size=${pasteData.size}, limit=$maxSize"
        }
        pastePullCursorManager.persistDiscardedMaxCreateTime(
            appInstanceId = pasteData.appInstanceId,
            createTime = pasteData.createTime,
        )
        val deviceName =
            syncRuntimeInfoDao
                .getSyncRuntimeInfo(pasteData.appInstanceId)
                ?.getDeviceDisplayName()
                ?: pasteData.appInstanceId
        notificationManager.sendNotification(
            title = { it.getText("remote_non_file_paste_discarded_too_large", deviceName) },
            messageType = MessageType.Warning,
        )
        return true
    }

    /**
     * Discard (not fail) a remote file paste whose metadata violates the
     * transfer resource limits. The pull cursor has already advanced past the
     * batch this paste arrived in, so propagating a failure would abort
     * [releaseRemotePasteDataList] and silently drop unrelated pastes pulled in
     * the same round — including pastes from other devices.
     */
    private suspend fun discardInvalidRemoteFilePaste(
        pasteData: PasteData,
        cause: Throwable,
    ) {
        logger.warn(cause) {
            "Discard invalid remote file paste from ${pasteData.appInstanceId}: " +
                "createTime=${pasteData.createTime}"
        }
        pastePullCursorManager.persistDiscardedMaxCreateTime(
            appInstanceId = pasteData.appInstanceId,
            createTime = pasteData.createTime,
        )
        val deviceName =
            syncRuntimeInfoDao
                .getSyncRuntimeInfo(pasteData.appInstanceId)
                ?.getDeviceDisplayName()
                ?: pasteData.appInstanceId
        notificationManager.sendNotification(
            title = { it.getText("remote_file_paste_discarded_invalid", deviceName) },
            messageType = MessageType.Warning,
        )
    }

    suspend fun releaseRemotePasteData(
        pasteData: PasteData,
        tryWritePasteboard: (PasteData) -> Unit,
    ): Result<Unit> {
        return runCatching {
            if (discardOversizedRemoteNonFilePaste(pasteData)) {
                return@runCatching
            }
            val remotePasteDataId = pasteData.id
            val isFileType = pasteData.isFileType()
            if (isFileType) {
                try {
                    validateFileTransferMetadata(pasteData)
                } catch (e: IllegalArgumentException) {
                    discardInvalidRemoteFilePaste(pasteData, e)
                    return@runCatching
                }
            }
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

    suspend fun releaseRemotePasteDataList(
        pasteDataList: List<PasteData>,
        releaseLast: suspend (PasteData) -> Result<Unit?>,
    ): Result<Unit?> {
        if (pasteDataList.isEmpty()) return Result.success(null)

        for (index in 0 until pasteDataList.lastIndex) {
            releaseRemotePasteData(pasteDataList[index]) { _ -> }
                .onFailure { return Result.failure(it) }
        }

        return releaseLast(pasteDataList.last())
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
                validateFileTransferMetadata(pasteData)
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

    /**
     * Rolls back a successful [releaseRemotePasteDataForPush] whose prepared
     * paste could not be attached to a push session. Mirrors the session-expiry path in
     * [com.crosspaste.sync.PushSessionManager.sweepExpired]: marking the
     * LOADING row deleted lets the regular delete pipeline reclaim the
     * preallocated file slots.
     */
    suspend fun discardPushPrepared(pasteId: Long): Result<Unit> =
        withContext(NonCancellable) {
            var lastFailure: Throwable? = null
            repeat(DISCARD_PUSH_PREPARED_ATTEMPTS) { attempt ->
                val result = pasteDao.markDeletePasteData(pasteId)
                if (result.isSuccess) return@withContext result
                lastFailure = result.exceptionOrNull()
                if (attempt < DISCARD_PUSH_PREPARED_ATTEMPTS - 1) {
                    delay(DISCARD_PUSH_PREPARED_RETRY_DELAY)
                }
            }
            Result.failure(lastFailure ?: IllegalStateException("Failed to discard prepared paste $pasteId"))
        }
}
