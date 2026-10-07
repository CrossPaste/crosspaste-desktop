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
import com.crosspaste.utils.ZipWriter
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
            // Only paste.data and the count marker are staged here; files are streamed
            // straight from storage into the package.
            val basePath = tempDir.resolve("export-$epochMilliseconds", true)
            exportTempPath = basePath
            userDataPathProvider.autoCreateDir(basePath)
            val exportFileName = "crosspaste-export-$epochMilliseconds.data"
            val bufferedSink =
                pasteExportParam.exportBufferedSink(exportFileName) ?: run {
                    logger.error { "can't write to export output" }
                    throw PasteException(
                        StandardErrorCode.EXPORT_FAIL.toErrorCode(),
                        "can't write to export output",
                    )
                }
            var nextIndex = 1L
            var exportError = false
            val zipResult =
                try {
                    runCatching {
                        compressUtils.openZip(bufferedSink).use { zip ->
                            val pasteDataFile = basePath.resolve("paste.data")
                            fileUtils.writeFile(pasteDataFile) { sink ->
                                runCatching {
                                    val exportCount = pasteDao.getExportNum(pasteExportParam)
                                    pasteDao.batchReadPasteData(
                                        readPasteDataList = { id, limit ->
                                            pasteDao.getExportPasteData(id, limit, pasteExportParam)
                                        },
                                        dealPasteData = { pasteData ->
                                            nextIndex +=
                                                exportPasteData(zip, pasteExportParam, nextIndex, pasteData, sink)
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
                            if (exportedCount > 0L) {
                                zip.addPath(pasteDataFile.name, pasteDataFile)
                                val countFile = basePath.resolve("$exportedCount.count")
                                fileUtils.createFile(countFile)
                                zip.addPath(countFile.name, countFile)
                            }
                        }
                    }
                } finally {
                    runCatching { bufferedSink.close() }
                }
            val exportedCount = nextIndex - 1
            if (zipResult.isFailure || exportedCount == 0L) {
                // A truncated package looks like a valid backup but cannot be imported,
                // and an empty one is useless
                pasteExportParam.discardExport(exportFileName)
            }
            zipResult.onFailure { e ->
                logger.error(e) { "compress export file fail" }
                throw PasteException(
                    StandardErrorCode.EXPORT_FAIL.toErrorCode(),
                    "compress export file fail",
                )
            }
            if (exportError && exportedCount == 0L) {
                notificationManager.sendNotification(
                    title = { it.getText("export_fail") },
                    messageType = MessageType.Error,
                )
            } else if (exportedCount > 0L) {
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

    /**
     * Returns 1 if the paste was written, 0 if it was left out. Anything that would leave
     * it out is checked before its first entry is written; a failure while streaming its
     * files propagates and stops the export, because the next paste would reuse this
     * index and collide with the entries already written.
     */
    private fun exportPasteData(
        zip: ZipWriter,
        pasteExportParam: PasteExportParam,
        index: Long,
        pasteData: PasteData,
        sink: BufferedSink,
    ): Long {
        val prepared =
            runCatching {
                val pasteFilesList = pasteData.getPasteAppearItems().filterIsInstance<PasteFiles>()
                // A record whose files are not in the package cannot be imported, so a paste
                // over the size limit or with a missing file is left out whole rather than
                // exported without its files
                val maxFileSize = pasteExportParam.maxFileSize
                if (maxFileSize != null && pasteFilesList.any { it.size > maxFileSize }) {
                    return 0L
                }
                val filePaths = pasteFilesList.flatMap { it.getFilePaths(userDataPathProvider) }
                if (filePaths.any { !fileUtils.existFile(it) }) {
                    logger.warn { "export skips pasteData with missing files, id = ${pasteData.id}" }
                    return 0L
                }
                if (filePaths.map { it.name }.distinct().size != filePaths.size) {
                    logger.warn { "export skips pasteData with duplicate file names, id = ${pasteData.id}" }
                    return 0L
                }
                val json = pasteData.toStoredJson()
                Pair(filePaths, codecsUtils.base64Encode(json.encodeToByteArray()))
            }.getOrElse { e ->
                logger.error(e) { "export pasteData fail, id = ${pasteData.id}" }
                return 0L
            }

        val (filePaths, encoded) = prepared
        for (filePath in filePaths) {
            zip.addPath("${pasteData.appInstanceId}/$index/${filePath.name}", filePath)
        }
        sink.write(encoded.encodeToByteArray())
        sink.writeUtf8("\n")
        return 1L
    }
}
