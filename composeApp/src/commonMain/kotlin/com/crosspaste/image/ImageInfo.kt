package com.crosspaste.image

import com.crosspaste.info.PasteInfo

val EMPTY_IMAGE_INFO = ImageInfo(mapOf())

data class ImageInfo(val map: Map<String, PasteInfo>)

class ImageInfoBuilder {
    private val map = mutableMapOf<String, PasteInfo>()

    fun add(pasteInfo: PasteInfo): ImageInfoBuilder {
        map[pasteInfo.key] = pasteInfo
        return this
    }

    fun build(): ImageInfo {
        return ImageInfo(map)
    }
}
