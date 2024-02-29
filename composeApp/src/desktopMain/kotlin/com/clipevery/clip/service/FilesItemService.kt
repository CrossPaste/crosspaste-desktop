package com.clipevery.clip.service

import com.clipevery.app.AppFileType
import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.clip.item.FilesClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.utils.DesktopFileUtils
import com.clipevery.utils.DesktopFileUtils.copyFile
import com.clipevery.utils.DesktopFileUtils.createClipRelativePath
import com.clipevery.utils.md5ByArray
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

class FilesItemService: ClipItemService {

    companion object FilesItemService {

        const val FILE_LIST_ID = "application/x-java-file-list"
    }

    override fun getIdentifiers(): List<String> {
        return listOf(FILE_LIST_ID)
    }

    override fun createPreClipItem(
        clipId: Int,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        FilesClipItem().apply {
            this.identifier = identifier
        }.let {
            clipCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(
        transferData: Any,
        clipId: Int,
        itemIndex: Int,
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        if (transferData is List<*>) {
            val files = transferData.filterIsInstance<File>()

            val md5List = mutableListOf<String>()
            val relativePathList = mutableListOf<String>()

            for (file in files) {
                val fileName = file.name
                val relativePath = createClipRelativePath(clipId, fileName)
                relativePathList.add(relativePath)
                val filePath = DesktopFileUtils.createClipPath(relativePath, isFile = true, AppFileType.FILE)
                if (copyFile(file.toPath(), filePath)) {
                    val md5 = DesktopFileUtils.getFileMd5(filePath)
                    md5List.add(md5)
                } else {
                    throw IllegalStateException("Failed to copy file")
                }
            }

            val update: (ClipAppearItem, MutableRealm) -> Unit = { clipItem, realm ->
                realm.query(FilesClipItem::class).query("id == $0", clipItem.id).first().find()?.apply {
                    this.relativePathList = relativePathList.toRealmList()
                    this.md5 = if (md5List.size == 1) md5List[0] else md5ByArray(md5List.toTypedArray())
                }
            }

            if (files.isNotEmpty()) {
                clipCollector.updateCollectItem(itemIndex, this::class, update)
            }
        }
    }

}