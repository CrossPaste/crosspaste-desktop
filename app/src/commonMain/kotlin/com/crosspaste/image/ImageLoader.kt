package com.crosspaste.image

import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.utils.Loader
import okio.Path

interface FaviconLoader : Loader<String, Path>

interface FileExtImageLoader : Loader<Path, Path>

interface ThumbnailLoader : Loader<PasteFileCoordinate, Path> {

    // Based on the original path, calculate the thumbnail path
    fun getThumbnailPath(pasteFileCoordinate: PasteFileCoordinate): Path

    fun getOriginMetaPath(pasteFileCoordinate: PasteFileCoordinate): Path

    fun readOriginMeta(
        pasteFileCoordinate: PasteFileCoordinate,
        imageInfoBuilder: ImageInfoBuilder,
    )
}
