package com.clipevery.clip.item

import com.clipevery.clip.ClipItemType
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class HtmlTextClipItem(override val text: String, override val html: String):
    ClipItem, ClipHtml, ClipText {

    override val clipItemType: ClipItemType = ClipItemType.HtmlText
    override fun toTransferable(): Transferable {
        return ClipHtmlTextTransferable(this)
    }
}

class ClipHtmlTextTransferable(private val clipItem: HtmlTextClipItem): Transferable {
    override fun getTransferData(flavor: DataFlavor): Any {
        return when (flavor) {
            DataFlavor.stringFlavor -> {
                clipItem.text
            }
            DataFlavor.allHtmlFlavor -> {
                clipItem.html
            }
            else -> {
                throw Exception("No supported get transfer data for flavor: ${flavor.mimeType}")
            }
        }
    }

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
        return flavor == DataFlavor.stringFlavor || flavor == DataFlavor.allHtmlFlavor
    }

    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return arrayOf(DataFlavor.allHtmlFlavor, DataFlavor.stringFlavor)
    }

}