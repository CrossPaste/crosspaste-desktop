package com.crosspaste.paste.plugin.type

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteDataFlavors
import com.crosspaste.paste.PasteDataFlavors.URL_FLAVOR
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.utils.FileNameNormalizer
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.noOptionParent
import okio.Path.Companion.toOkioPath
import java.awt.datatransfer.DataFlavor
import java.io.ByteArrayInputStream
import java.io.File

class DesktopFilesTypePlugin(
    private val appInfo: AppInfo,
    private val configManager: CommonConfigManager,
    private val platform: Platform,
    private val userDataPathProvider: UserDataPathProvider,
) : FilesTypePlugin {

    companion object {

        const val FILE_LIST_ID = "application/x-java-file-list"
    }

    private val fileUtils = getFileUtils()

    override fun getPasteType(): PasteType = PasteType.FILE_TYPE

    override fun getIdentifiers(): List<String> = listOf(FILE_LIST_ID)

    override fun createPrePasteItem(
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        createFilesPasteItem(
            identifiers = listOf(identifier),
            relativePathList = listOf(),
            fileInfoTreeMap = mapOf(),
        ).let {
            pasteCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(
        transferData: Any,
        pasteId: Long,
        itemIndex: Int,
        dataFlavor: PasteDataFlavor,
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        if (transferData is List<*>) {
            val files = transferData.filterIsInstance<File>()

            if (files.isEmpty()) {
                return
            }

            val paths = files.map { it.toOkioPath(normalize = true) }

            val parentPath = paths[0].noOptionParent

            val fileInfoTrees = mutableMapOf<String, FileInfoTree>()
            val relativePathList = mutableListOf<String>()

            val maxBackupFileSize =
                fileUtils.bytesSize(
                    configManager.getCurrentConfig().maxBackupFileSize,
                )

            val sumFileSize =
                fileUtils.calculateTotalSizeWithLimit(
                    paths,
                    maxBackupFileSize,
                )

            val copySizeExceeding = maxBackupFileSize < sumFileSize

            val copyFromCrossPaste =
                files.any {
                    it.startsWith(userDataPathProvider.getUserDataPath().toFile())
                }

            // If the file size exceeds the limit
            // or the file is copied from the cross-paste directory
            // the file will not be copied
            // Instead, record the file path
            val useRefCopyFiles = copySizeExceeding || copyFromCrossPaste

            for (path in paths) {
                val originFileName = path.name
                val fileName = FileNameNormalizer.normalize(originFileName)

                if (useRefCopyFiles) {
                    relativePathList.add(originFileName)
                    fileInfoTrees[originFileName] = fileUtils.getFileInfoTree(path)
                } else {
                    val relativePath =
                        fileUtils.createPasteRelativePath(
                            pasteCoordinate =
                                PasteCoordinate(
                                    id = pasteId,
                                    appInstanceId = appInfo.appInstanceId,
                                ),
                            fileName = fileName,
                        )
                    relativePathList.add(relativePath)
                    val filePath =
                        fileUtils.createPastePath(
                            relativePath,
                            isFile = true,
                            AppFileType.FILE,
                            userDataPathProvider,
                        )
                    if (fileUtils.copyPath(path, filePath).isSuccess) {
                        fileInfoTrees[originFileName] = fileUtils.getFileInfoTree(filePath)
                    } else {
                        throw IllegalStateException("Failed to copy file")
                    }
                }
            }

            val update: (PasteItem) -> PasteItem = { pasteItem ->
                createFilesPasteItem(
                    identifiers = pasteItem.identifiers,
                    basePath = if (useRefCopyFiles) parentPath.toString() else null,
                    relativePathList = relativePathList,
                    fileInfoTreeMap = fileInfoTrees,
                    extraInfo = pasteItem.extraInfo,
                )
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }

    override fun buildTransferable(
        pasteItem: PasteItem,
        mixedCategory: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as FilesPasteItem
        val fileList: List<File> = pasteItem.getFilePaths(userDataPathProvider).map { it.toFile() }
        map[DataFlavor.javaFileListFlavor.toPasteDataFlavor()] = fileList
        if (mixedCategory) {
            map[PasteDataFlavors.URI_LIST_FLAVOR.toPasteDataFlavor()] =
                ByteArrayInputStream(
                    fileList
                        .joinToString(separator = "\n") { it.absolutePath }
                        .encodeToByteArray(),
                )
            map[DataFlavor.stringFlavor.toPasteDataFlavor()] =
                fileList.joinToString(separator = "\n") { it.name }

            if (fileList.size == 1) {
                map[URL_FLAVOR.toPasteDataFlavor()] = fileList[0].toURI().toURL()
            }
        }

        if (platform.isLinux()) {
            val content =
                fileList.joinToString(
                    separator = "\n",
                    prefix = "copy\n",
                ) { it.toURI().toString() }
            val inputStream = ByteArrayInputStream(content.encodeToByteArray())
            map[PasteDataFlavors.GNOME_COPIED_FILES_FLAVOR.toPasteDataFlavor()] = inputStream
        }
    }
}
