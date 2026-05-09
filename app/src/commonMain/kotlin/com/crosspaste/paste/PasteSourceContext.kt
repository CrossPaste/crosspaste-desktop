package com.crosspaste.paste

data class PasteSourceContext(
    val source: String?,
    val remote: Boolean,
    val dragAndDrop: Boolean = false,
    val targetAppInstanceIds: Set<String>? = null,
)
