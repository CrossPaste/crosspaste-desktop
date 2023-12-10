package com.clipevery.clip.item

import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

class ImageFileClipItem(override val image: Image,
                        override val file: File,
                        override val text: String): FileClipItem(file, text), ClipImage {

    override val clipItemType: ClipItemType = ClipItemType.ImageFile

    override fun toTransferable(): Transferable {
        return ClipImageFileTransferable(this)
    }
}

class ClipImageFileTransferable(private val clipItem: ImageFileClipItem): ClipFileTransferable(clipItem) {
    override fun getTransferData(flavor: DataFlavor): Any {
        return when (flavor) {
            DataFlavor.imageFlavor -> {
                clipItem.image
            }
            else -> {
                super.getTransferData(flavor)
            }
        }
    }

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
        return flavor == DataFlavor.imageFlavor || super.isDataFlavorSupported(flavor)
    }

    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return super.getTransferDataFlavors() + DataFlavor.imageFlavor
    }
}