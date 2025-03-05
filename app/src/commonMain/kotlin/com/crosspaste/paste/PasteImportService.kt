package com.crosspaste.paste

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteState
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageObject
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getCompressUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path

class PasteImportService(
    private val copywriter: GlobalCopywriter,
    private val notificationManager: NotificationManager,
    private val pasteDao: PasteDao,
    private val userDataPathProvider: UserDataPathProvider,
) {
    private val logger = KotlinLogging.logger { }

    private val codecsUtils = getCodecsUtils()

    private val compressUtils = getCompressUtils()

    private val fileUtils = getFileUtils()

    fun import(
        importFilePath: Path,
        updateProgress: (Float) -> Unit,
    ) {
        var importTempPath: Path? = null
        try {
            val tempDir = userDataPathProvider.resolve(appFileType = AppFileType.TEMP)
            val epochMilliseconds = DateUtils.nowEpochMilliseconds()
            val basePath = tempDir.resolve("import-$epochMilliseconds", true)
            importTempPath = basePath
            userDataPathProvider.autoCreateDir(basePath)
            decompress(importFilePath, basePath)
            val importCount =
                fileUtils.listFiles(basePath) {
                    it.name.endsWith(".count")
                }.first().name.removeSuffix(".count").toLong()
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
                try {
                    val pasteData = readPasteData(line)
                    if (importPasteData(basePath, count, pasteData)) {
                        count++
                        updateProgress(count.toFloat() / importCount.toFloat())
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error importing paste data, index = $count" }
                    failCount++
                }
            }
            if (count > 0) {
                notificationManager.sendNotification(
                    MessageObject(
                        message = copywriter.getText("import_successful"),
                        messageType = MessageType.Success,
                    ),
                )
            } else {
                notificationManager.sendNotification(
                    MessageObject(
                        message = copywriter.getText("no_data_import"),
                        messageType = MessageType.Warning,
                    ),
                )
            }
            updateProgress(1f)
        } catch (e: Exception) {
            logger.error(e) { "Error importing paste data" }
            notificationManager.sendNotification(
                MessageObject(
                    message = copywriter.getText("import_fail"),
                    messageType = MessageType.Error,
                ),
            )
        } finally {
            importTempPath?.let { fileUtils.deleteFile(it) }
        }
    }

    private fun importPasteData(
        basePath: Path,
        index: Long,
        pasteData: PasteData,
    ): Boolean {
        try {
            val id = pasteDao.createPasteData(pasteData)

            val pasteCoordinate = pasteData.getPasteCoordinate()
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
                        PasteData.createSearchContent(
                            pasteData.source,
                            pasteData.pasteAppearItem?.getSearchContent(),
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
            return true
        } catch (e: Exception) {
            logger.error(e) { "Error importing paste data, index = $index" }
            return false
        }
    }

    private fun moveResource(
        basePath: Path,
        index: Long,
        importPasteData: PasteData,
        pasteFiles: PasteFiles,
    ) {
        val path =
            basePath.resolve(importPasteData.appInstanceId)
                .resolve(index.toString())

        for (filePath in pasteFiles.getFilePaths(userDataPathProvider)) {
            val importFilePath = path.resolve(filePath.name)
            fileUtils.moveFile(importFilePath, filePath)
        }
    }

    private fun readPasteData(line: String): PasteData {
        val json = codecsUtils.base64Decode(line).decodeToString()
        return PasteData.fromJson(json)
    }

    private fun decompress(
        importFilePath: Path,
        decompressPath: Path,
    ) {
        val result = compressUtils.unzip(importFilePath, decompressPath)
        if (result.isFailure) {
            throw PasteException(
                errorCode = StandardErrorCode.IMPORT_FAIL.toErrorCode(),
                message = "Failed to decompress the file",
            )
        }
    }
}
