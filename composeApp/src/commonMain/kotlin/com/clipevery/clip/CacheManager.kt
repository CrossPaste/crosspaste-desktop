package com.clipevery.clip

import com.clipevery.dto.pull.PullFilesKey
import com.clipevery.presist.FilesIndex

interface CacheManager {

    fun getFilesIndex(pullFilesKey: PullFilesKey): FilesIndex?
}
