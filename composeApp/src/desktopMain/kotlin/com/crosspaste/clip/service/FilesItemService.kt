package com.crosspaste.clip.service

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.clip.ClipCollector
import com.crosspaste.clip.ClipItemService
import com.crosspaste.clip.item.FilesClipItem
import com.crosspaste.dao.clip.ClipItem
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.utils.DesktopFileUtils
import com.crosspaste.utils.DesktopFileUtils.copyPath
import com.crosspaste.utils.DesktopFileUtils.createClipRelativePath
import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.getEncryptUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import kotlinx.serialization.encodeToString
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import kotlin.io.path.absolutePathString

class FilesItemService(appInfo: AppInfo) : ClipItemService(appInfo) {

    companion object FilesItemService {

        const val FILE_LIST_ID = "application/x-java-file-list"

        private val encryptUtils = getEncryptUtils()
    }

    override fun getIdentifiers(): List<String> {
        return listOf(FILE_LIST_ID)
    }

    override fun createPreClipItem(
        clipId: Long,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        clipCollector: ClipCollector,
    ) {
        FilesClipItem().apply {
            this.identifiers = realmListOf(identifier)
        }.let {
            clipCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(
        transferData: Any,
        clipId: Long,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        clipCollector: ClipCollector,
    ) {
        if (transferData is List<*>) {
            val files = transferData.filterIsInstance<File>()
            val fileInfoTrees = mutableMapOf<String, FileInfoTree>()
            val relativePathList = mutableListOf<String>()

            for (file in files) {
                val path = file.toPath()

                if (path.absolutePathString().startsWith(DesktopPathProvider.clipUserPath.absolutePathString())) {
                    continue
                }

                val fileName = file.name
                val relativePath =
                    createClipRelativePath(
                        appInstanceId = appInfo.appInstanceId,
                        clipId = clipId,
                        fileName = fileName,
                    )
                relativePathList.add(relativePath)
                val filePath = DesktopFileUtils.createClipPath(relativePath, isFile = true, AppFileType.FILE)
                if (copyPath(path, filePath)) {
                    fileInfoTrees[file.name] = DesktopFileUtils.getFileInfoTree(filePath)
                } else {
                    throw IllegalStateException("Failed to copy file")
                }
            }

            val relativePathRealmList = relativePathList.toRealmList()
            val fileInfoTreeJsonString = DesktopJsonUtils.JSON.encodeToString(fileInfoTrees)
            val md5 = encryptUtils.md5ByArray(files.mapNotNull { fileInfoTrees[it.name]?.md5 }.toTypedArray())
            val count = fileInfoTrees.map { it.value.getCount() }.sum()
            val size = fileInfoTrees.map { it.value.size }.sum()

            val update: (ClipItem, MutableRealm) -> Unit = { clipItem, realm ->
                realm.query(FilesClipItem::class, "id == $0", clipItem.id).first().find()?.apply {
                    this.relativePathList = relativePathRealmList
                    this.fileInfoTree = fileInfoTreeJsonString
                    this.count = count
                    this.size = size
                    this.md5 = md5
                }
            }

            if (files.isNotEmpty()) {
                clipCollector.updateCollectItem(itemIndex, this::class, update)
            }
        }
    }
}
