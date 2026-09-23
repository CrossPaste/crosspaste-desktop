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
data class ReceiveFilesIndexResult(
    val filesIndex: FilesIndex,
    val renameMap: Map<String, String>,
)

fun buildFilesIndex(
    pasteData: PasteData,
    userDataPathProvider: UserDataPathProvider,
    chunkSize: Long,
): FilesIndex = buildFilesIndexInternal(pasteData, userDataPathProvider, chunkSize, prepareSlots = false).filesIndex

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
 * When a file lands in an external destination directory (`basePath != null`)
 * where a file of the same name already exists, [UserDataPathProvider.resolve]
 * renames it (e.g. `"big.apk"` -> `"big(1).apk"`). The resulting [ReceiveFilesIndexResult.renameMap]
 * must be applied to the stored [PasteData] so database metadata matches the
 * actual file slots allocated on disk.
 */
fun buildFilesIndexForReceive(
    pasteData: PasteData,
    userDataPathProvider: UserDataPathProvider,
    chunkSize: Long,
): ReceiveFilesIndexResult = buildFilesIndexInternal(pasteData, userDataPathProvider, chunkSize, prepareSlots = true)

private fun buildFilesIndexInternal(
    pasteData: PasteData,
    userDataPathProvider: UserDataPathProvider,
    chunkSize: Long,
    prepareSlots: Boolean,
): ReceiveFilesIndexResult {
    val dateUtils = getDateUtils()
    val dateString =
        dateUtils.getYMD(
            dateUtils.epochMillisecondsToLocalDateTime(pasteData.createTime),
        )
    val builder = FilesIndexBuilder(chunkSize)
    val combinedRenameMap = mutableMapOf<String, String>()
    pasteData.getPasteAppearItems().filterIsInstance<PasteFiles>().forEach { pasteFiles ->
        val effectivePasteFiles =
            if (combinedRenameMap.isEmpty()) {
                pasteFiles
            } else {
                pasteFiles.applyRenameMap(combinedRenameMap)
            }
        val renameMap =
            userDataPathProvider.resolve(
                pasteData.appInstanceId,
                dateString,
                pasteData.id,
                effectivePasteFiles,
                prepareSlots,
                builder,
                resolveConflicts = prepareSlots && combinedRenameMap.isEmpty(),
            )
        combinedRenameMap.putAll(renameMap)
    }
    return ReceiveFilesIndexResult(builder.build(), combinedRenameMap)
}
