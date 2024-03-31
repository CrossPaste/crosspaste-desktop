package com.clipevery.clip.item

import java.nio.file.Path

interface ClipHtml {

    var html: String

    fun getHtmlImagePath(): Path

}