package com.crosspaste.paste.item

interface PasteInit {

    fun init(
        appInstanceId: String,
        pasteId: Long,
    )
}
