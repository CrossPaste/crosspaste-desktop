package com.clipevery.clip.service

import com.clipevery.app.AppFileType
import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.clip.item.FileClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.utils.DesktopFileUtils
import com.clipevery.utils.DesktopFileUtils.copyFile
import com.clipevery.utils.DesktopFileUtils.createClipRelativePath
import io.realm.kotlin.MutableRealm
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

class FileItemService: ClipItemService {

    companion object FileItemService {

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
        FileClipItem().apply {
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
            if (files.size == 1) {
                val fileName = files[0].name
                val relativePath = createClipRelativePath(clipId, fileName)
                val filePath = DesktopFileUtils.createClipPath(relativePath, isFile = true, AppFileType.FILE)
                if (copyFile(files[0].toPath(), filePath)) {
                    val md5 = DesktopFileUtils.getFileMd5(filePath)

                    val update: (ClipAppearItem, MutableRealm) -> Unit = { clipItem, realm ->
                        realm.query(FileClipItem::class).query("id == $0", clipItem.id).first().find()?.apply {
                            this.relativePath = relativePath
                            this.md5 = md5
                        }
                    }
                    clipCollector.updateCollectItem(itemIndex, this::class, update)
                }
            } else if (files.size > 1) {
                // todo multi files
            }
        }
    }

}