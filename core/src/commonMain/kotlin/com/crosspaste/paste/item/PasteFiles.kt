package com.crosspaste.paste.item

import com.crosspaste.presist.FileInfoTree

interface PasteFiles {

    val count: Long

    val basePath: String?

    /**
     * Keyed by the top-level entry name (file name for a `SingleFileInfoTree`,
     * directory name for a `DirFileInfoTree`), NOT by [relativePathList]
     * entries: those may be multi-segment bound paths like
     * "appInstanceId/date/id/name.png" while the map key stays "name.png".
     * Derive keys via `relativePath.toPath().name`.
     */
    val fileInfoTreeMap: Map<String, FileInfoTree>

    val relativePathList: List<String>

    val size: Long

    fun getDirectChildrenCount(): Long = fileInfoTreeMap.size.toLong()

    fun isRefFiles(): Boolean = basePath != null

    fun applyRenameMap(renameMap: Map<String, String>): PasteFiles

    fun computeRenamedFileData(renameMap: Map<String, String>): Pair<List<String>, Map<String, FileInfoTree>> {
        val newRelativePathList = relativePathList.map { renameMap[it] ?: it }
        val newFileInfoTreeMap = fileInfoTreeMap.entries.associate { (k, v) -> (renameMap[k] ?: k) to v }
        return newRelativePathList to newFileInfoTreeMap
    }
}
