package com.crosspaste.paste

data class PasteSourceContext(
    val source: String?,
    val remote: Boolean,
    val appleRemoteClipboard: Boolean = false,
    val dragAndDrop: Boolean = false,
    val targetAppInstanceIds: Set<String>? = null,
)
