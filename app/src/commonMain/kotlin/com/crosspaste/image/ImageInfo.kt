package com.crosspaste.image

import com.crosspaste.info.PasteInfo

data class ImageInfo(
    val map: Map<String, PasteInfo>,
)

class ImageInfoBuilder {
    private val map = mutableMapOf<String, PasteInfo>()

    fun add(pasteInfo: PasteInfo): ImageInfoBuilder {
        map[pasteInfo.key] = pasteInfo
        return this
    }

    fun build(): ImageInfo = ImageInfo(map)
}
