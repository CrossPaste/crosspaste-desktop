package com.crosspaste.paste.service

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteItemService
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.utils.DesktopFileUtils
import com.crosspaste.utils.DesktopFileUtils.copyPath
import com.crosspaste.utils.DesktopFileUtils.createPasteRelativePath
import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.getCodecsUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import kotlinx.serialization.encodeToString
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import kotlin.io.path.absolutePathString

class FilesItemService(appInfo: AppInfo) : PasteItemService(appInfo) {

    companion object FilesItemService {

        const val FILE_LIST_ID = "application/x-java-file-list"

        private val codecsUtils = getCodecsUtils()
    }

    override fun getIdentifiers(): List<String> {
        return listOf(FILE_LIST_ID)
    }

    override fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
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
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        pasteCollector: PasteCollector,
    ) {
        if (transferData is List<*>) {
            val files = transferData.filterIsInstance<File>()
            val fileInfoTrees = mutableMapOf<String, FileInfoTree>()
            val relativePathList = mutableListOf<String>()

            for (file in files) {
                val path = file.toPath()

                if (path.absolutePathString().startsWith(DesktopPathProvider.pasteUserPath.absolutePathString())) {
                    continue
                }

                val fileName = file.name
                val relativePath =
                    createPasteRelativePath(
                        appInstanceId = appInfo.appInstanceId,
                        pasteId = pasteId,
                        fileName = fileName,
                    )
                relativePathList.add(relativePath)
                val filePath = DesktopFileUtils.createPastePath(relativePath, isFile = true, AppFileType.FILE)
                if (copyPath(path, filePath)) {
                    fileInfoTrees[file.name] = DesktopFileUtils.getFileInfoTree(filePath)
                } else {
                    throw IllegalStateException("Failed to copy file")
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
                    this.size = size
                    this.md5 = md5
                }
            }

            if (files.isNotEmpty()) {
                pasteCollector.updateCollectItem(itemIndex, this::class, update)
            }
        }
    }
}
