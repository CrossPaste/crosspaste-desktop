package com.clipevery.clip.item

import java.io.File

interface ClipFile {

    fun getFile(): File

    fun getExtension(): String
}