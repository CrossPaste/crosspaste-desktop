package com.crosspaste.image

import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.utils.Loader
import okio.Path

interface VideoThumbnailLoader : Loader<PasteFileCoordinate, Path> {

    fun getThumbnailPath(pasteFileCoordinate: PasteFileCoordinate): Path
}
