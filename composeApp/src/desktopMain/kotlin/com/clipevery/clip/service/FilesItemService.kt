package com.clipevery.clip.service

import com.clipevery.app.AppFileType
import com.clipevery.app.AppInfo
import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.clip.item.FilesClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.presist.FileInfoTree
import com.clipevery.utils.DesktopFileUtils
import com.clipevery.utils.DesktopFileUtils.copyPath
import com.clipevery.utils.DesktopFileUtils.createClipRelativePath
import com.clipevery.utils.DesktopJsonUtils
import com.clipevery.utils.EncryptUtils.md5ByArray
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import kotlinx.serialization.encodeToString
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

class FilesItemService(appInfo: AppInfo) : ClipItemService(appInfo) {

    companion object FilesItemService {

        const val FILE_LIST_ID = "application/x-java-file-list"
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
                val fileName = file.name
                val relativePath =
                    createClipRelativePath(
                        appInstanceId = appInfo.appInstanceId,
                        clipId = clipId,
                        fileName = fileName,
                    )
                relativePathList.add(relativePath)
                val filePath = DesktopFileUtils.createClipPath(relativePath, isFile = true, AppFileType.FILE)
                if (copyPath(file.toPath(), filePath)) {
                    fileInfoTrees[file.name] = DesktopFileUtils.getFileInfoTree(filePath)
                } else {
                    throw IllegalStateException("Failed to copy file")
                }
            }

            val relativePathRealmList = relativePathList.toRealmList()
            val fileInfoTreeJsonString = DesktopJsonUtils.JSON.encodeToString(fileInfoTrees)
            val md5 = md5ByArray(files.mapNotNull { fileInfoTrees[it.name]?.md5 }.toTypedArray())
            val count = fileInfoTrees.map { it.value.getCount() }.sum()
            val size = fileInfoTrees.map { it.value.size }.sum()

            val update: (ClipAppearItem, MutableRealm) -> Unit = { clipItem, realm ->
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
