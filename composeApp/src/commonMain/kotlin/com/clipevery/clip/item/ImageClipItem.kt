package com.clipevery.clip.item

import com.clipevery.clip.ClipItemType
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class ImageClipItem(override val image: Image): ClipItem, ClipImage {

    override val clipItemType: ClipItemType = ClipItemType.Image

    override fun toTransferable(): Transferable {
        return ClipImageTransferable(this)
    }
}

class ClipImageTransferable(private val clipItem: ImageClipItem): Transferable {
    override fun getTransferData(flavor: DataFlavor): Any {
        return when (flavor) {
            DataFlavor.imageFlavor -> {
                clipItem.image
            }
            else -> {
                throw Exception("No supported get transfer data for flavor: ${flavor.mimeType}")
            }
        }
    }

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
        return flavor == DataFlavor.imageFlavor
    }

    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return arrayOf(DataFlavor.imageFlavor)
    }

}