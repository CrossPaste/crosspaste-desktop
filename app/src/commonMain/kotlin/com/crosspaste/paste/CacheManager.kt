package com.crosspaste.paste

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndex
import com.crosspaste.presist.buildFilesIndex
import com.crosspaste.sync.FilePullService

interface CacheManager {

    val pasteDao: PasteDao

    val userDataPathProvider: UserDataPathProvider

    suspend fun getFilesIndex(id: Long): FilesIndex?

    fun loadKey(id: Long): FilesIndex? =
        pasteDao.getLoadedPasteDataBlock(id)?.let { pasteData ->
            buildFilesIndex(pasteData, userDataPathProvider, FilePullService.CHUNK_SIZE)
        }
}
