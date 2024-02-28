package com.clipevery.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

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

    @Composable
    fun getExtImagePainter(ext: String): Painter? =
        extImageMap[ext]?.let { painterResource("file-ext/$it.png") }
}