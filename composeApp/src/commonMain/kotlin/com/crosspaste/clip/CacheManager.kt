package com.crosspaste.clip

import com.crosspaste.dto.pull.PullFilesKey
import com.crosspaste.presist.FilesIndex

interface CacheManager {

    fun getFilesIndex(pullFilesKey: PullFilesKey): FilesIndex?
}
