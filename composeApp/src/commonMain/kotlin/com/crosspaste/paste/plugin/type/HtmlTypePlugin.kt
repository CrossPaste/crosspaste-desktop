package com.crosspaste.paste.plugin.type

interface HtmlTypePlugin : PasteTypePlugin {

    fun normalizeHtml(
        html: String,
        source: String?,
    ): String
}
