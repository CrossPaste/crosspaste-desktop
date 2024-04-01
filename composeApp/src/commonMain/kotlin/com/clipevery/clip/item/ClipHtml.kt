package com.clipevery.clip.item

import java.nio.file.Path

interface ClipHtml: ClipInit {

    var html: String

    fun getHtmlImagePath(): Path

}