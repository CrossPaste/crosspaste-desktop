package com.clipevery.clip

import androidx.compose.ui.graphics.ImageBitmap
import com.clipevery.dto.pull.PullFilesKey
import com.clipevery.presist.FilesIndex
import com.google.common.cache.LoadingCache
import java.nio.file.Path

interface CacheManager {

    val filesIndexCache: LoadingCache<PullFilesKey, FilesIndex>

    val imagesCaches: LoadingCache<Path, ImageBitmap>
}