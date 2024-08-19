package com.crosspaste.image

import com.crosspaste.utils.Loader
import okio.Path

interface FaviconLoader : Loader<String, Path>

interface FileExtImageLoader : Loader<Path, Path>

interface ThumbnailLoader : Loader<Path, Path> {

    // Based on the original path, calculate the thumbnail path
    fun getThumbnailPath(path: Path): Path

    fun getOriginMetaPath(path: Path): Path

    fun readOriginMeta(
        path: Path,
        imageInfoBuilder: ImageInfoBuilder,
    )
}
