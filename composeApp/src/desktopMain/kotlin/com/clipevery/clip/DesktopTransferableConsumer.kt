package com.clipevery.clip

import com.clipevery.clip.item.ClipItem
import com.clipevery.clip.item.FileClipItem
import com.clipevery.clip.item.FilesClipItem
import com.clipevery.clip.item.HtmlTextClipItem
import com.clipevery.clip.item.ImageClipItem
import com.clipevery.clip.item.ImageFileClipItem
import com.clipevery.clip.item.SUPPORT_VIDEO_EXTENSIONS
import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.clip.item.VideoFileClipItem
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.net.MalformedURLException
import java.net.URL

open class DesktopTransferableConsumer: TransferableConsumer {

    private val logger = KotlinLogging.logger {}

    override fun consume(transferable: Transferable) {
    }

    protected fun toFinelyClipItem(clipItem: ClipItem?): ClipItem? {
        return clipItem?.let {
            return when (it.clipItemType) {
                ClipItemType.Text -> {
                    val textClipItem = it as TextClipItem
                    return try {
                        val url = URL(textClipItem.text)
                        UrlClipItem(url)
                    } catch (e: MalformedURLException) {
                        textClipItem
                    }
                }
                ClipItemType.File -> {
                    val fileClipItem = it as FileClipItem
                    val extension = fileClipItem.extension
                    if (SUPPORT_VIDEO_EXTENSIONS.contains(extension)) {
                        return VideoFileClipItem(fileClipItem.file, fileClipItem.text)
                    }
                    return fileClipItem
                }
                else -> {
                    it
                }
            }
        }
    }

    private fun createClipItem(transferable: Transferable): ClipItem? {
        val text: String? = getText(transferable)
        val image: Image? = getImage(transferable)
        val files: List<File> = getFiles(transferable)

        if (text != null) {
            val html: String? = getHtml(transferable)
            if (html != null) {
                return HtmlTextClipItem(text, html)
            }
            if (image == null && files.isEmpty()) {
                return TextClipItem(text)
            }
            if (image != null && files.size == 1) {
                return ImageFileClipItem(image, files[0], text)
            }
        }

        if (image != null && files.isEmpty()) {
            return ImageClipItem(image)
        }

        if (files.size == 1) {
            return FileClipItem(files[0], files[0].name)
        }

        if (files.size > 1) {
            return FilesClipItem(files)
        }

        logger.warn { "Failed to create clip item from transferable" }
        return null
    }


    private fun getText(transferable: Transferable): String? {
        return if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            try {
                val obj: Any? = transferable.getTransferData(DataFlavor.stringFlavor)
                if (obj is String) {
                    obj
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.warn { "Failed to get text from transferable: ${e.message}" }
                null
            }
        } else {
            null
        }
    }

    private fun getHtml(transferable: Transferable): String? {
        return getAllHtml(transferable) ?:
        getSelectionHtml(transferable) ?:
        getFragmentHtml(transferable)
    }

    private fun getAllHtml(transferable: Transferable): String? {
        return if (transferable.isDataFlavorSupported(DataFlavor.allHtmlFlavor)) {
            val obj: Any? = transferable.getTransferData(DataFlavor.allHtmlFlavor)
            if (obj is String) {
                obj
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun getSelectionHtml(transferable: Transferable): String? {
        return if (transferable.isDataFlavorSupported(DataFlavor.selectionHtmlFlavor)) {
            val obj: Any? = transferable.getTransferData(DataFlavor.selectionHtmlFlavor)
            if (obj is String) {
                obj
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun getFragmentHtml(transferable: Transferable): String? {
        return if (transferable.isDataFlavorSupported(DataFlavor.fragmentHtmlFlavor)) {
            val obj: Any? = transferable.getTransferData(DataFlavor.fragmentHtmlFlavor)
            if (obj is String) {
                obj
            } else {
                null
            }
        } else {
            null
        }
    }



    private fun getImage(transferable: Transferable): Image? {
        return if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            val obj: Any? = transferable.getTransferData(DataFlavor.imageFlavor)
            if (obj is Image) {
                obj
            } else {
                null
            }
        } else {
            null
        }
    }


    @Suppress("UNCHECKED_CAST")
    private fun getFiles(transferable: Transferable): List<File> {
        return if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val obj: Any? = transferable.getTransferData(DataFlavor.javaFileListFlavor)
            return if (obj is List<*>) {
                obj as List<File>
            } else {
                listOf()
            }
        } else {
            listOf()
        }
    }
}