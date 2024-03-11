package com.clipevery.clip

import com.clipevery.clip.item.FilesClipItem
import com.clipevery.clip.item.HtmlClipItem
import com.clipevery.clip.item.ImagesClipItem
import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.clip.ClipData
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

class DesktopTransferableProducer: TransferableProducer {
    override fun produce(clipData: ClipData): Transferable {
        val map = LinkedHashMap<DataFlavor, Any>()

        val clipAppearItems = clipData.getClipAppearItems()

        for (clipAppearItem in clipAppearItems) {
            when(clipAppearItem) {
                is FilesClipItem -> {
                    addDataFlavor(map, clipAppearItem)
                }
                is HtmlClipItem -> {
                    addDataFlavor(map, clipAppearItem)
                }
                is ImagesClipItem -> {
                    addDataFlavor(map, clipAppearItem)
                }
                is TextClipItem -> {
                    addDataFlavor(map, clipAppearItem)
                }
                is UrlClipItem -> {
                    addDataFlavor(map, clipAppearItem)
                }
            }
        }
        return ClipTransferable(map)
    }

    private fun addDataFlavor(map: MutableMap<DataFlavor, Any>, filesClipItem: FilesClipItem) {
        val fileList: List<File> = filesClipItem.getFilePaths().map { it.toFile() }
        map[DataFlavor.javaFileListFlavor] = fileList
    }

    private fun addDataFlavor(map: MutableMap<DataFlavor, Any>, htmlClipItem: HtmlClipItem) {
        map[DataFlavor.selectionHtmlFlavor] = htmlClipItem.html
    }

    private fun addDataFlavor(map: MutableMap<DataFlavor, Any>, imagesClipItem: ImagesClipItem) {
        val filePaths = imagesClipItem.getFilePaths()
        if (filePaths.size == 1) {
            val imageFile = filePaths[0].toFile()
            val bufferedImage: Image = ImageIO.read(imageFile)
            map[DataFlavor.imageFlavor] = bufferedImage
            map[DataFlavor.javaFileListFlavor] = listOf(imageFile)
        } else {
            val fileList: List<File> = imagesClipItem.getFilePaths().map { it.toFile() }
            map[DataFlavor.javaFileListFlavor] = fileList
        }
    }

    private fun addDataFlavor(map: MutableMap<DataFlavor, Any>, textClipItem: TextClipItem) {
        map[DataFlavor.stringFlavor] = textClipItem.text
    }

    private fun addDataFlavor(map: MutableMap<DataFlavor, Any>, urlClipItem: UrlClipItem) {
        map[DataFlavor("application/x-java-url; class=java.net.URL")] = URL(urlClipItem.url)
    }
}