package com.clipevery.clip

import com.clipevery.dto.pull.PullFilesKey
import com.clipevery.presist.FilesIndex
import com.google.common.cache.LoadingCache

interface CacheManager {

    val filesIndexCache: LoadingCache<PullFilesKey, FilesIndex>

}