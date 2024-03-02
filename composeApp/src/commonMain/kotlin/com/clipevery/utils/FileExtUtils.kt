package com.clipevery.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

object FileExtUtils {

    private val extIconImageMap = mapOf(
        Pair("pdf", "pdf"),
        Pair("doc", "word"),
        Pair("docx", "word"),
        Pair("ppt", "ppt"),
        Pair("pptx", "ppt"),
        Pair("xls", "excel"),
        Pair("xlsx", "excel"),
        Pair("pages", "pages"),
        Pair("key", "keynote"),
        Pair("numbers", "numbers")
    )

    private val canPreviewImageMap = setOf(
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "tiff", "svg")

    fun canPreviewImage(ext: String): Boolean = canPreviewImageMap.contains(ext.lowercase())

    @Composable
    fun getExtPreviewImagePainter(ext: String): Painter? =
        extIconImageMap[ext]?.let { painterResource("file-ext/$it.png") }
}