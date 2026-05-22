package com.crosspaste.presist

import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getDateUtils

/**
 * Canonical [PasteData] → [FilesIndex] mapping. Resolves each [PasteFiles] item
 * through [userDataPathProvider] to compute on-disk chunk slots.
 *
 * Used by both directions of file sync:
 * - Pull (serving `/pull/file`): build from a LOADED PasteData to read chunks out.
 * - Push (receiving `/sync/file/push`): build from a freshly-bound LOADING PasteData
 *   so incoming chunks land in the right slots.
 */
fun buildFilesIndex(
    pasteData: PasteData,
    userDataPathProvider: UserDataPathProvider,
    chunkSize: Long,
): FilesIndex {
    val dateUtils = getDateUtils()
    val dateString =
        dateUtils.getYMD(
            dateUtils.epochMillisecondsToLocalDateTime(pasteData.createTime),
        )
    val builder = FilesIndexBuilder(chunkSize)
    pasteData.getPasteAppearItems().filterIsInstance<PasteFiles>().forEach { pasteFiles ->
        userDataPathProvider.resolve(
            pasteData.appInstanceId,
            dateString,
            pasteData.id,
            pasteFiles,
            false,
            builder,
        )
    }
    return builder.build()
}
