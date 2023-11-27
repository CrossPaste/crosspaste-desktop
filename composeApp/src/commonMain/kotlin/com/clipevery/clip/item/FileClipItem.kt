package com.clipevery.clip.item

import com.clipevery.clip.ClipItemType
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

open class FileClipItem(override val file: File, override val text: String):
    ClipItem, ClipFile, ClipText {

    override val clipItemType: ClipItemType = ClipItemType.File

    override fun toTransferable(): Transferable {
        return ClipFileTransferable(this)
    }
}

open class ClipFileTransferable(private val clipItem: FileClipItem): Transferable {
    override fun getTransferData(flavor: DataFlavor): Any {
        return when (flavor) {
            DataFlavor.stringFlavor -> {
                clipItem.text
            }
            DataFlavor.javaFileListFlavor -> {
                listOf(clipItem.file)
            }
            else -> {
                throw Exception("No supported get transfer data for flavor: ${flavor.mimeType}")
            }
        }
    }

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
        return flavor == DataFlavor.stringFlavor || flavor == DataFlavor.javaFileListFlavor
    }

    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return arrayOf(DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor)
    }

}