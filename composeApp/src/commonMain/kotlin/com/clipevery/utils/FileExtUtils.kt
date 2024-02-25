package com.clipevery.utils

import androidx.compose.ui.graphics.ImageBitmap

object FileExtUtils {

    private val extImageMap = mapOf(
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

    fun getExtImage(ext: String): ImageBitmap? =
        extImageMap[ext]?.let { ResourceUtils.loadImageBitmap("file-ext/$it.png") }
}