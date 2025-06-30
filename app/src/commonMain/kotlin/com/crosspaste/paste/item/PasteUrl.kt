package com.crosspaste.paste.item

interface PasteUrl {
    val url: String

    fun getTitle(): String?
}
