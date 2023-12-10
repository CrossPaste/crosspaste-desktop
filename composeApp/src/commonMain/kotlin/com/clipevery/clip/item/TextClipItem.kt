package com.clipevery.clip.item

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

open class TextClipItem(override val text: String): ClipItem, ClipText {
    override val clipItemType: ClipItemType = ClipItemType.Text

    override fun toTransferable(): Transferable {
        return ClipTextTransferable(this)
    }
}

class ClipTextTransferable(private val clipItem: TextClipItem): Transferable {
    override fun getTransferData(flavor: DataFlavor): Any {
        return when (flavor) {
            DataFlavor.stringFlavor -> {
                clipItem.text
            }
            else -> {
                throw Exception("No supported get transfer data for flavor: ${flavor.mimeType}")
            }
        }
    }

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
        return flavor == DataFlavor.stringFlavor
    }

    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return arrayOf(DataFlavor.stringFlavor)
    }

}