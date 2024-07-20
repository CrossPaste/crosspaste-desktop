package com.crosspaste.image

import okio.Path

interface ImageLoader<T, R> {

    fun load(value: T): R?
}

interface FaviconLoader : ImageLoader<String, Path>

interface FileExtImageLoader : ImageLoader<Path, Path>

interface ThumbnailLoader : ImageLoader<Path, Path> {

    // Based on the original path, calculate the thumbnail path
    fun getThumbnailPath(path: Path): Path
}
