package com.crosspaste.presist

import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getDateUtils

/**
 * Canonical [PasteData] → [FilesIndex] mapping. Resolves each [PasteFiles] item
 * through [userDataPathProvider] to compute on-disk chunk slots.
 *
 * Use this on the **sender** side — pull-serving (`/pull/file`) and push-sending
 * ([com.crosspaste.sync.FilePushService.pushFiles]) — where the on-disk files
 * already exist and must only be indexed. For the **receiver** side of push,
 * use [buildFilesIndexForReceive] so parent directories and empty file slots
 * get created before incoming chunks try to write into them.
 */
fun buildFilesIndex(
    pasteData: PasteData,
    userDataPathProvider: UserDataPathProvider,
    chunkSize: Long,
): FilesIndex = buildFilesIndexInternal(pasteData, userDataPathProvider, chunkSize, prepareSlots = false)

/**
 * Receive-side counterpart of [buildFilesIndex] for the push protocol.
 *
 * In addition to building the [FilesIndex] over the paste's destination paths,
 * this also creates the parent directories and pre-allocates empty files at
 * each target path — so the subsequent `/sync/file/push` chunk uploads can
 * `RandomAccessFile`-seek into them. Without this preparation, the very first
 * chunk hits `FileNotFoundException` because `RandomAccessFile("rw")` does not
 * create missing parent directories.
 *
 * The pull-receive path achieves the same effect by calling
 * [com.crosspaste.path.UserDataPathProvider.resolve] with `isPull = true`
 * inside `FilePullService`. Push-receive lives in a different code path
 * ([com.crosspaste.paste.PasteReleaseService.releaseRemotePasteDataForPush]),
 * which is why it needs an explicit entry point.
 */
fun buildFilesIndexForReceive(
    pasteData: PasteData,
    userDataPathProvider: UserDataPathProvider,
    chunkSize: Long,
): FilesIndex = buildFilesIndexInternal(pasteData, userDataPathProvider, chunkSize, prepareSlots = true)

private fun buildFilesIndexInternal(
    pasteData: PasteData,
    userDataPathProvider: UserDataPathProvider,
    chunkSize: Long,
    prepareSlots: Boolean,
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
            prepareSlots,
            builder,
        )
    }
    return builder.build()
}
