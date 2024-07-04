package com.crosspaste.paste

import com.crosspaste.dto.pull.PullFilesKey
import com.crosspaste.presist.FilesIndex

interface CacheManager {

    fun getFilesIndex(pullFilesKey: PullFilesKey): FilesIndex?
}
