package com.clipevery.clip.item

import com.clipevery.clip.ClipItemType
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

class FilesClipItem(override val fileList: List<File>): ClipItem, ClipFileList {
    override val clipItemType: ClipItemType = ClipItemType.Files

    override fun toTransferable(): Transferable {
        return ClipFilesTransferable(this)
    }
}

class ClipFilesTransferable(private val clipItem: FilesClipItem): Transferable {
    override fun getTransferData(flavor: DataFlavor): Any {
        return when (flavor) {
            DataFlavor.javaFileListFlavor -> {
                clipItem.fileList
            }
            else -> {
                throw Exception("No supported get transfer data for flavor: ${flavor.mimeType}")
            }
        }
    }

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
        return flavor == DataFlavor.javaFileListFlavor
    }

    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return arrayOf(DataFlavor.javaFileListFlavor)
    }

}