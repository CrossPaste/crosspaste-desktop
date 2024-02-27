package com.clipevery.clip.item

import androidx.compose.ui.graphics.ImageBitmap
import java.nio.file.Path

interface ClipHtml {

    var html: String

    fun getHtmlImagePath(): Path

    fun getHtmlImage(): ImageBitmap?
}