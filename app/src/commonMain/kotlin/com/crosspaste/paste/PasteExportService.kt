package com.crosspaste.paste

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getCompressUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.BufferedSink
import okio.Path

class PasteExportService(
    private val pasteDao: PasteDao,
    private val userDataPathProvider: UserDataPathProvider,
) {
    private val logger = KotlinLogging.logger { }

    private val codecsUtils = getCodecsUtils()

    private val compressUtils = getCompressUtils()

    private val fileUtils = getFileUtils()

    fun export(pasteExportParam: PasteExportParam) {
        val tempDir = userDataPathProvider.resolve(appFileType = AppFileType.TEMP)
        val ymdhms = DateUtils.getYMDHMS()
        val basePath = tempDir.resolve("export-$ymdhms")
        userDataPathProvider.autoCreateDir(basePath)
        var pasteDataFile = basePath.resolve("paste.data")
        var count = 0L
        fileUtils.writeFile(pasteDataFile) { sink ->
            try {
                pasteDao.batchReadPasteData { pasteData ->
                    if (pasteExportParam.filterPasteData(pasteData)) {
                        count += exportPasteData(basePath, pasteExportParam, pasteData, sink)
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "read pasteData list fail" }
            }
        }
        if (count > 0L) {
            compressExportFile(basePath, pasteExportParam.exportPath, ymdhms)
        }
    }

    private fun exportPasteData(
        basePath: Path,
        pasteExportParam: PasteExportParam,
        pasteData: PasteData,
        sink: BufferedSink,
    ): Long {
        try {
            val pasteFilesList =
                pasteData.getPasteAppearItems().filterIsInstance<PasteFiles>()
                    .filter { it.size <= pasteExportParam.maxFileSize }

            if (pasteFilesList.isEmpty()) {
                return 0L
            }

            for (pasteFiles in pasteFilesList) {
                copyResourceByPasteFiles(basePath, pasteData, pasteFiles)
            }

            val json = pasteData.toJson()
            val base64 = codecsUtils.base64Encode(json.encodeToByteArray())
            sink.write(base64.encodeToByteArray())
            sink.writeUtf8("\n")
            return 1L
        } catch (e: Exception) {
            logger.error(e) { "export pasteData fail, id = ${pasteData.id}" }
            return 0L
        }
    }

    private fun copyResourceByPasteFiles(
        basePath: Path,
        pasteData: PasteData,
        pasteFiles: PasteFiles,
    ) {
        val dateString =
            DateUtils.getYMD(
                DateUtils.epochMillisecondsToLocalDateTime(pasteData.createTime),
            )

        val type =
            if (pasteFiles is FilesPasteItem) {
                "files"
            } else {
                "images"
            }

        val path =
            basePath.resolve(type)
                .resolve(pasteData.appInstanceId)
                .resolve(dateString)
                .resolve(pasteData.pasteId.toString())

        userDataPathProvider.autoCreateDir(path)

        for (pasteFile in pasteFiles.getPasteFiles(userDataPathProvider)) {
            fileUtils.copyPath(pasteFile.getFilePath(), path.resolve(pasteFile.getFilePath().name))
        }
    }

    fun compressExportFile(
        basePath: Path,
        exportPath: Path,
        ymdhms: String,
    ) {
        userDataPathProvider.autoCreateDir(exportPath)
        val targetZipFile = exportPath.resolve("crosspaste-export-$ymdhms.zip")
        compressUtils.zipDir(basePath, targetZipFile)
    }
}
