package com.crosspaste.paste

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndex
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.task.PullFileTaskExecutor
import com.crosspaste.utils.DateUtils

interface CacheManager {

    val dateUtils: DateUtils

    val pasteDao: PasteDao

    val userDataPathProvider: UserDataPathProvider

    suspend fun getFilesIndex(id: Long): FilesIndex?

    fun loadKey(id: Long): FilesIndex? =
        pasteDao.getLoadedPasteDataBlock(id)?.let { pasteData ->
            val dateString =
                dateUtils.getYMD(
                    dateUtils.epochMillisecondsToLocalDateTime(pasteData.createTime),
                )
            val filesIndexBuilder = FilesIndexBuilder(PullFileTaskExecutor.CHUNK_SIZE)
            val fileItems = pasteData.getPasteAppearItems().filter { it is PasteFiles }
            val pasteDataId = pasteData.id
            val appInstanceId = pasteData.appInstanceId
            for (pasteAppearItem in fileItems) {
                val pasteFiles = pasteAppearItem as PasteFiles
                userDataPathProvider.resolve(
                    appInstanceId,
                    dateString,
                    pasteDataId,
                    pasteFiles,
                    false,
                    filesIndexBuilder,
                )
            }
            filesIndexBuilder.build()
        }
}
