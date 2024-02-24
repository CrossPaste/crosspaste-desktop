package com.clipevery.clip.item

import androidx.compose.ui.graphics.ImageBitmap
import java.nio.file.Path

interface ClipImage {

    fun getImage(): ImageBitmap

    fun getImagePath(): Path
}