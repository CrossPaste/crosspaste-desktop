package com.crosspaste.paste

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getCompressUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.noOptionParent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path

class PasteImportService(
    private val notificationManager: NotificationManager,
    private val pasteDao: PasteDao,
    private val searchContentService: SearchContentService,
    private val userDataPathProvider: UserDataPathProvider,
) {
    private val logger = KotlinLogging.logger { }

    private val codecsUtils = getCodecsUtils()

    private val compressUtils = getCompressUtils()

    private val fileUtils = getFileUtils()

    private val ioCoroutineDispatcher = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val mutex = Mutex()

    fun import(
        pasteImportParam: PasteImportParam,
        updateProgress: (Float) -> Unit,
    ) {
        ioCoroutineDispatcher.launch {
            mutex.withLock {
                doImport(pasteImportParam, updateProgress)
            }
        }
    }

    private fun doImport(
        pasteImportParam: PasteImportParam,
        updateProgress: (Float) -> Unit,
    ) {
        var importTempPath: Path? = null
        runCatching {
            val tempDir = userDataPathProvider.resolve(appFileType = AppFileType.TEMP)
            val epochMilliseconds = DateUtils.nowEpochMilliseconds()
            val basePath = tempDir.resolve("import-$epochMilliseconds", true)
            importTempPath = basePath
            userDataPathProvider.autoCreateDir(basePath)
            decompress(pasteImportParam, basePath)
            val importCount =
                fileUtils
                    .listFiles(basePath) {
                        it.name.endsWith(".count")
                    }.first()
                    .name
                    .removeSuffix(".count")
                    .toLong()
            val pasteDataFile = basePath.resolve("paste.data")
            if (!fileUtils.existFile(pasteDataFile)) {
                throw PasteException(
                    StandardErrorCode.IMPORT_FAIL.toErrorCode(),
                    "Failed to find paste.data file",
                )
            }

            var count = 0L
            var failCount = 0L

            fileUtils.readByLines(pasteDataFile) { line ->
                count++
                readPasteData(line)?.let { pasteData ->
                    if (importPasteData(basePath, count, pasteData)) {
                        updateProgress(count.toFloat() / importCount.toFloat())
                    }
                } ?: run {
                    logger.error { "Error parsing paste data, index = $count" }
                    failCount++
                }
            }
            if (count > 0) {
                notificationManager.sendNotification(
                    title = { it.getText("import_successful") },
                    messageType = MessageType.Success,
                )
            } else {
                notificationManager.sendNotification(
                    title = { it.getText("no_data_import") },
                    messageType = MessageType.Warning,
                )
            }
            updateProgress(1f)
        }.onFailure { e ->
            updateProgress(-1f)
            logger.error(e) { "Error importing paste data" }
            notificationManager.sendNotification(
                title = { it.getText("import_fail") },
                messageType = MessageType.Error,
            )
        }.apply {
            importTempPath?.let { fileUtils.deleteFile(it) }
        }
    }

    private fun importPasteData(
        basePath: Path,
        index: Long,
        pasteData: PasteData,
    ): Boolean =
        runCatching {
            val id = pasteDao.createPasteData(pasteData)

            val pasteCoordinate = pasteData.getPasteCoordinate(id = index)
            val pasteAppearItem = pasteData.pasteAppearItem
            val pasteCollection = pasteData.pasteCollection

            val newPasteAppearItem = pasteAppearItem?.bind(pasteCoordinate)
            val newPasteCollection = pasteCollection.bind(pasteCoordinate)

            val importPasteData =
                pasteData.copy(
                    id = id,
                    pasteAppearItem = newPasteAppearItem,
                    pasteCollection = newPasteCollection,
                    pasteSearchContent =
                        searchContentService.createSearchContent(
                            pasteData.source,
                            newPasteAppearItem?.getSearchContent(),
                        ),
                )

            val pasteFilesList = importPasteData.getPasteAppearItems().filterIsInstance<PasteFiles>()

            if (pasteFilesList.isNotEmpty()) {
                pasteDao.updateFilePath(importPasteData)
            }
            for (pasteFiles in pasteFilesList) {
                moveResource(basePath, index, importPasteData, pasteFiles)
            }

            pasteDao.updatePasteState(id, PasteState.LOADED)
        }.onFailure { e ->
            logger.error(e) { "Error importing paste data, index = $index" }
        }.isSuccess

    private fun moveResource(
        basePath: Path,
        index: Long,
        importPasteData: PasteData,
        pasteFiles: PasteFiles,
    ) {
        val path =
            basePath
                .resolve(importPasteData.appInstanceId)
                .resolve(index.toString())

        for (filePath in pasteFiles.getFilePaths(userDataPathProvider)) {
            val importFilePath = path.resolve(filePath.name)
            userDataPathProvider.autoCreateDir(filePath.noOptionParent)
            fileUtils.moveFile(importFilePath, filePath)
        }
    }

    private fun readPasteData(line: String): PasteData? {
        val json = codecsUtils.base64Decode(line).decodeToString()
        return PasteData.fromJson(json)
    }

    private fun decompress(
        pasteImportParam: PasteImportParam,
        decompressPath: Path,
    ) {
        pasteImportParam.importBufferedSource()?.let { bufferSource ->
            compressUtils
                .unzip(bufferSource, decompressPath)
                .onSuccess {
                    runCatching {
                        bufferSource.close()
                    }
                }.onFailure {
                    logger.error(it) { "Failed to decompress the file" }
                    throw PasteException(
                        errorCode = StandardErrorCode.IMPORT_FAIL.toErrorCode(),
                        message = "Failed to decompress the file",
                    )
                }
        } ?: run {
            throw PasteException(
                errorCode = StandardErrorCode.IMPORT_FAIL.toErrorCode(),
                message = "Failed to read the file",
            )
        }
    }
}
