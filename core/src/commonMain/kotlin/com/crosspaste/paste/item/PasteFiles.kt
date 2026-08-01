package com.crosspaste.paste.item

import com.crosspaste.presist.FileInfoTree

interface PasteFiles {

    val count: Long

    val basePath: String?

    /**
     * Describes the file payload copied in one clipboard operation.
     *
     * A paste can contain multiple top-level entries, and each entry can be either
     * a file or a directory. For internally stored pastes, `appInstanceId/date/id/`
     * is the paste root; its direct children are the entries copied together.
     * [relativePathList] stores paths to those children, for example
     * `appInstanceId/date/id/report.pdf` and `appInstanceId/date/id/photos`.
     *
     * This map has one entry for each direct child of that paste root and is keyed
     * only by the child's name: `"report.pdf"` maps to a `SingleFileInfoTree`, while
     * `"photos"` maps to a `DirFileInfoTree` containing its descendants. Therefore,
     * derive the key from the final component of the corresponding relative path
     * (`relativePath.toPath().name`), not from the full relative path or from names
     * nested inside a directory tree.
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
