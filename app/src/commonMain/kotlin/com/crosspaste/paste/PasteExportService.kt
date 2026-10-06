package com.crosspaste.paste

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getCompressUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.BufferedSink
import okio.Path

class PasteExportService(
    private val notificationManager: NotificationManager,
    private val pasteDao: PasteDao,
    private val userDataPathProvider: UserDataPathProvider,
) {
    private val logger = KotlinLogging.logger { }

    private val codecsUtils = getCodecsUtils()

    private val compressUtils = getCompressUtils()

    private val fileUtils = getFileUtils()

    private val ioCoroutineDispatcher = namedScope(ioDispatcher, "PasteExportService")

    private val mutex = Mutex()

    fun export(
        pasteExportParam: PasteExportParam,
        updateProgress: (Float) -> Unit,
    ) {
        ioCoroutineDispatcher.launch {
            mutex.withLock {
                doExport(pasteExportParam, updateProgress)
            }
        }
    }

    private suspend fun doExport(
        pasteExportParam: PasteExportParam,
        updateProgress: (Float) -> Unit,
    ) {
        var exportTempPath: Path? = null
        runCatching {
            val tempDir = userDataPathProvider.resolve(appFileType = AppFileType.TEMP)
            val epochMilliseconds = DateUtils.nowEpochMilliseconds()
            val basePath = tempDir.resolve("export-$epochMilliseconds", true)
            exportTempPath = basePath
            userDataPathProvider.autoCreateDir(basePath)
            val pasteDataFile = basePath.resolve("paste.data")
            var nextIndex = 1L
            var exportError = false
            fileUtils.writeFile(pasteDataFile) { sink ->
                runCatching {
                    val exportCount = pasteDao.getExportNum(pasteExportParam)
                    pasteDao.batchReadPasteData(
                        readPasteDataList = { id, limit ->
                            pasteDao.getExportPasteData(id, limit, pasteExportParam)
                        },
                        dealPasteData = { pasteData ->
                            nextIndex += exportPasteData(basePath, pasteExportParam, nextIndex, pasteData, sink)
                            val exportedCount = nextIndex - 1
                            val currentProgress =
                                if (exportedCount < exportCount) {
                                    exportedCount.toFloat() / exportCount.toFloat()
                                } else {
                                    0.99f
                                }
                            updateProgress(currentProgress)
                        },
                    )
                }.onFailure { e ->
                    exportError = true
                    logger.error(e) { "read pasteData list fail" }
                }
            }
            val exportedCount = nextIndex - 1
            val exportFileName = "crosspaste-export-$epochMilliseconds.data"
            if (exportError && exportedCount == 0L) {
                notificationManager.sendNotification(
                    title = { it.getText("export_fail") },
                    messageType = MessageType.Error,
                )
            } else if (exportedCount > 0L) {
                val countFile = basePath.resolve("$exportedCount.count")
                fileUtils.createFile(countFile)
                compressExportFile(basePath, pasteExportParam, exportFileName)
                if (exportError) {
                    notificationManager.sendNotification(
                        title = { it.getText("export_partial") },
                        message = { exportFileName },
                        messageType = MessageType.Warning,
                        duration = null,
                    )
                } else {
                    notificationManager.sendNotification(
                        title = { it.getText("export_successful") },
                        message = { exportFileName },
                        messageType = MessageType.Success,
                        duration = null,
                    )
                }
            } else {
                notificationManager.sendNotification(
                    title = { it.getText("nothing_to_export") },
                    messageType = MessageType.Warning,
                )
            }
            updateProgress(1f)
        }.onFailure { e ->
            // to set export failed
            updateProgress(-1f)
            logger.error(e) { "export pasteData failed" }
            notificationManager.sendNotification(
                title = { it.getText("export_fail") },
                messageType = MessageType.Error,
            )
        }.apply {
            exportTempPath?.let {
                fileUtils.deleteFile(it)
            }
        }
    }

    private fun exportPasteData(
        basePath: Path,
        pasteExportParam: PasteExportParam,
        index: Long,
        pasteData: PasteData,
        sink: BufferedSink,
    ): Long =
        runCatching {
            val pasteFilesList = pasteData.getPasteAppearItems().filterIsInstance<PasteFiles>()
            // A record whose files are not in the package cannot be imported, so a paste
            // over the size limit is left out whole rather than exported without its files
            val maxFileSize = pasteExportParam.maxFileSize
            if (maxFileSize != null && pasteFilesList.any { it.size > maxFileSize }) {
                return 0L
            }

            try {
                for (pasteFiles in pasteFilesList) {
                    copyResource(basePath, index, pasteData, pasteFiles)
                }
            } catch (e: Exception) {
                // The next paste reuses this index, so do not leave these files for it
                fileUtils.fileSystem.deleteRecursively(resourceDir(basePath, index, pasteData), mustExist = false)
                throw e
            }

            val json = pasteData.toStoredJson()
            val base64 = codecsUtils.base64Encode(json.encodeToByteArray())
            sink.write(base64.encodeToByteArray())
            sink.writeUtf8("\n")
            1L
        }.getOrElse { e ->
            logger.error(e) { "export pasteData fail, id = ${pasteData.id}" }
            0L
        }

    private fun resourceDir(
        basePath: Path,
        index: Long,
        pasteData: PasteData,
    ): Path =
        basePath
            .resolve(pasteData.appInstanceId)
            .resolve(index.toString())

    private fun copyResource(
        basePath: Path,
        index: Long,
        pasteData: PasteData,
        pasteFiles: PasteFiles,
    ) {
        val path = resourceDir(basePath, index, pasteData)

        userDataPathProvider.autoCreateDir(path)

        for (filePath in pasteFiles.getFilePaths(userDataPathProvider)) {
            fileUtils.copyPath(filePath, path.resolve(filePath.name)).getOrThrow()
        }
    }

    private fun compressExportFile(
        basePath: Path,
        pasteExportParam: PasteExportParam,
        exportFileName: String,
    ) {
        pasteExportParam.exportBufferedSink(exportFileName)?.let { bufferedSink ->
            val zipResult =
                try {
                    compressUtils.zipDir(basePath, bufferedSink)
                } finally {
                    runCatching { bufferedSink.close() }
                }
            zipResult.onFailure {
                logger.error { "compress export file fail" }
                // A truncated package looks like a valid backup but cannot be imported
                pasteExportParam.discardExport(exportFileName)
                throw PasteException(
                    StandardErrorCode.EXPORT_FAIL.toErrorCode(),
                    "compress export file fail",
                )
            }
        } ?: run {
            logger.error { "can't write to export output" }
            throw PasteException(
                StandardErrorCode.EXPORT_FAIL.toErrorCode(),
                "can't write to export output",
            )
        }
    }
}
