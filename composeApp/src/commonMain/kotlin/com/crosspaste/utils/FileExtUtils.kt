package com.crosspaste.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadImageBitmap

object FileExtUtils {

    private val extIconImageMap =
        mapOf(
            Pair("pdf", "pdf"),
            Pair("doc", "word"),
            Pair("docx", "word"),
            Pair("ppt", "ppt"),
            Pair("pptx", "ppt"),
            Pair("xls", "excel"),
            Pair("xlsx", "excel"),
            Pair("pages", "pages"),
            Pair("key", "keynote"),
            Pair("numbers", "numbers"),
        )

    private val canPreviewImageMap =
        setOf(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "tiff", "svg",
        )

    fun canPreviewImage(ext: String): Boolean = canPreviewImageMap.contains(ext.lowercase())

    @OptIn(ExperimentalComposeUiApi::class)
    fun getExtPreviewImage(ext: String): ImageBitmap? =
        extIconImageMap[ext]?.let {
            val fileName = "file-ext/$it.png"
            val inputStream = ResourceLoader.Default.load(fileName)
            inputStream.use { stream -> stream.buffered().use(::loadImageBitmap) }
        }

    @Composable
    fun getExtPreviewImagePainter(ext: String): Painter? {
        return getExtPreviewImage(ext)?.let { imageBitmap ->
            BitmapPainter(imageBitmap)
        }
    }
}
