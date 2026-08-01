package com.crosspaste.presist

import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.PasteFiles

object FileTransferResourceLimits {
    // Current peers use 1 MiB chunks and default to a 512 MiB sender limit.
    // These protocol ceilings leave substantial headroom without permitting
    // unbounded task lists or receive-side file preallocation.
    const val MAX_CHUNK_SIZE: Long = 16L * 1024 * 1024
    const val MAX_CHUNK_COUNT: Int = 16_384
    const val MAX_TREE_DEPTH: Int = 64
    const val MAX_TREE_NODES: Int = 10_000
    const val MAX_SINGLE_FILE_SIZE: Long = 8L * 1024 * 1024 * 1024
    const val MAX_TOTAL_SIZE: Long = 8L * 1024 * 1024 * 1024
    const val MAX_ICON_SIZE: Long = 4L * 1024 * 1024
    const val MAX_SESSION_TOKEN_LENGTH: Int = 512
}

internal data class FileTransferValidationLimits(
    val maxTreeDepth: Int = FileTransferResourceLimits.MAX_TREE_DEPTH,
    val maxTreeNodes: Int = FileTransferResourceLimits.MAX_TREE_NODES,
    val maxSingleFileSize: Long = FileTransferResourceLimits.MAX_SINGLE_FILE_SIZE,
    val maxTotalSize: Long = FileTransferResourceLimits.MAX_TOTAL_SIZE,
)

internal data class FileTransferMetadataStats(
    val fileCount: Long,
    val totalSize: Long,
)

fun validateFileTransferMetadata(pasteData: PasteData) {
    val pasteFilesItems = pasteData.getPasteAppearItems().filterIsInstance<PasteFiles>()
    require(pasteFilesItems.isNotEmpty()) { "file paste must contain PasteFiles metadata" }
    validateFileTransferMetadata(pasteFilesItems, FileTransferValidationLimits())
}

internal fun validateFileTransferMetadata(
    pasteFilesItems: List<PasteFiles>,
    limits: FileTransferValidationLimits,
): FileTransferMetadataStats {
    require(limits.maxTreeDepth > 0) { "maxTreeDepth must be positive" }
    require(limits.maxTreeNodes > 0) { "maxTreeNodes must be positive" }
    require(limits.maxSingleFileSize >= 0) { "maxSingleFileSize must be non-negative" }
    require(limits.maxTotalSize >= 0) { "maxTotalSize must be non-negative" }
    require(pasteFilesItems.isNotEmpty()) { "file paste must contain PasteFiles metadata" }

    var nodeCount = 0
    var totalFileCount = 0L
    var totalSize = 0L

    fun addSize(
        current: Long,
        addition: Long,
        limit: Long,
        description: String,
    ): Long {
        require(addition <= limit - current) { "$description exceeds $limit bytes" }
        return current + addition
    }

    fun validateTree(
        fileInfoTree: FileInfoTree,
        depth: Int,
    ): FileTransferMetadataStats {
        require(depth <= limits.maxTreeDepth) {
            "file tree depth exceeds ${limits.maxTreeDepth}"
        }
        nodeCount += 1
        require(nodeCount <= limits.maxTreeNodes) {
            "file tree node count exceeds ${limits.maxTreeNodes}"
        }
        require(fileInfoTree.size >= 0) { "file tree size must be non-negative" }

        return when (fileInfoTree) {
            is SingleFileInfoTree -> {
                require(fileInfoTree.size <= limits.maxSingleFileSize) {
                    "single file size exceeds ${limits.maxSingleFileSize} bytes"
                }
                FileTransferMetadataStats(fileCount = 1, totalSize = fileInfoTree.size)
            }

            is DirFileInfoTree -> {
                var directoryFileCount = 0L
                var directorySize = 0L
                fileInfoTree.iterator().forEach { (_, child) ->
                    val childStats = validateTree(child, depth + 1)
                    directoryFileCount += childStats.fileCount
                    directorySize =
                        addSize(
                            current = directorySize,
                            addition = childStats.totalSize,
                            limit = limits.maxTotalSize,
                            description = "directory size",
                        )
                }
                require(fileInfoTree.size == directorySize) {
                    "directory size does not match its children"
                }
                FileTransferMetadataStats(directoryFileCount, directorySize)
            }
        }
    }

    pasteFilesItems.forEach { pasteFiles ->
        require(pasteFiles.relativePathList.size == pasteFiles.fileInfoTreeMap.size) {
            "relativePathList size does not match fileInfoTreeMap"
        }
        require(pasteFiles.size >= 0) { "PasteFiles size must be non-negative" }
        require(pasteFiles.count >= 0) { "PasteFiles count must be non-negative" }

        var itemFileCount = 0L
        var itemSize = 0L
        pasteFiles.fileInfoTreeMap.values.forEach { fileInfoTree ->
            val stats = validateTree(fileInfoTree, depth = 1)
            itemFileCount += stats.fileCount
            itemSize =
                addSize(
                    current = itemSize,
                    addition = stats.totalSize,
                    limit = limits.maxTotalSize,
                    description = "PasteFiles size",
                )
        }
        require(pasteFiles.count == itemFileCount) {
            "PasteFiles count does not match file tree"
        }
        require(pasteFiles.size == itemSize) {
            "PasteFiles size does not match file tree"
        }

        totalFileCount += itemFileCount
        totalSize =
            addSize(
                current = totalSize,
                addition = itemSize,
                limit = limits.maxTotalSize,
                description = "file transfer size",
            )
    }

    require(totalFileCount > 0) { "file paste must contain at least one file" }
    return FileTransferMetadataStats(totalFileCount, totalSize)
}
