package com.crosspaste.paste.plugin.type

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteDataFlavors
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.utils.DesktopFileUtils
import com.crosspaste.utils.DesktopFileUtils.copyPath
import com.crosspaste.utils.DesktopFileUtils.createPasteRelativePath
import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.noOptionParent
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import kotlinx.serialization.encodeToString
import okio.Path.Companion.toOkioPath
import java.awt.datatransfer.DataFlavor
import java.io.ByteArrayInputStream
import java.io.File

class FilesTypePlugin(
    private val appInfo: AppInfo,
    private val configManager: ConfigManager,
) : PasteTypePlugin {

    companion object FilesTypePlugin {

        const val FILE_LIST_ID = "application/x-java-file-list"

        private val codecsUtils = getCodecsUtils()
    }

    private val fileUtils = getFileUtils()

    override fun getPasteType(): Int {
        return PasteType.FILE
    }

    override fun getIdentifiers(): List<String> {
        return listOf(FILE_LIST_ID)
    }

    override fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        FilesPasteItem().apply {
            this.identifiers = realmListOf(identifier)
        }.let {
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

            val parentPath = files[0].toOkioPath().noOptionParent

            val fileInfoTrees = mutableMapOf<String, FileInfoTree>()
            val relativePathList = mutableListOf<String>()

            val sumFileSize = files.sumOf { it.length() }

            val copySizeExceeding = fileUtils.bytesSize(configManager.config.maxBackupFileSize) < sumFileSize

            val copyFromCrossPaste =
                files.any {
                    it.startsWith(DesktopPathProvider.pasteUserPath.toFile())
                }

            // If the file size exceeds the limit
            // or the file is copied from the cross-paste directory
            // the file will not be copied
            // Instead, record the file path
            val useRefCopyFiles = copySizeExceeding || copyFromCrossPaste

            for (file in files) {
                val path = file.toOkioPath(normalize = true)
                val fileName = file.name

                if (useRefCopyFiles) {
                    val relativePath = path.name
                    relativePathList.add(relativePath)
                    fileInfoTrees[file.name] = DesktopFileUtils.getFileInfoTree(path)
                } else {
                    val relativePath =
                        createPasteRelativePath(
                            appInstanceId = appInfo.appInstanceId,
                            pasteId = pasteId,
                            fileName = fileName,
                        )
                    relativePathList.add(relativePath)
                    val filePath =
                        DesktopFileUtils.createPastePath(
                            relativePath,
                            isFile = true,
                            AppFileType.FILE,
                        )
                    if (copyPath(path, filePath)) {
                        fileInfoTrees[file.name] = DesktopFileUtils.getFileInfoTree(filePath)
                    } else {
                        throw IllegalStateException("Failed to copy file")
                    }
                }
            }

            val relativePathRealmList = relativePathList.toRealmList()
            val fileInfoTreeJsonString = DesktopJsonUtils.JSON.encodeToString(fileInfoTrees)
            val md5 = codecsUtils.md5ByArray(files.mapNotNull { fileInfoTrees[it.name]?.md5 }.toTypedArray())
            val count = fileInfoTrees.map { it.value.getCount() }.sum()
            val size = fileInfoTrees.map { it.value.size }.sum()

            val update: (PasteItem, MutableRealm) -> Unit = { pasteItem, realm ->
                realm.query(FilesPasteItem::class, "id == $0", pasteItem.id).first().find()?.apply {
                    this.relativePathList = relativePathRealmList
                    this.fileInfoTree = fileInfoTreeJsonString
                    this.count = count
                    this.basePath = if (useRefCopyFiles) parentPath.toString() else null
                    this.size = size
                    this.md5 = md5
                }
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }

    override fun buildTransferable(
        pasteItem: PasteItem,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as FilesPasteItem
        val fileList: List<File> = pasteItem.getFilePaths().map { it.toFile() }
        map[DataFlavor.javaFileListFlavor.toPasteDataFlavor()] = fileList
        map[PasteDataFlavors.URI_LIST_FLAVOR.toPasteDataFlavor()] =
            ByteArrayInputStream(fileList.joinToString(separator = "\n") { it.absolutePath }.toByteArray())
        map[DataFlavor.stringFlavor.toPasteDataFlavor()] = fileList.joinToString(separator = "\n") { it.name }

        if (currentPlatform().isLinux()) {
            val content =
                fileList.joinToString(
                    separator = "\n",
                    prefix = "copy\n",
                ) { it.toURI().toString() }
            val inputStream = ByteArrayInputStream(content.toByteArray())
            map[PasteDataFlavors.GNOME_COPIED_FILES_FLAVOR.toPasteDataFlavor()] = inputStream
        }
    }
}
