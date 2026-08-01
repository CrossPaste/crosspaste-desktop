package com.crosspaste.presist

import okio.Path

class FilesIndexBuilder(
    private val chunkSize: Long,
) {

    init {
        require(chunkSize in 1..FileTransferResourceLimits.MAX_CHUNK_SIZE) {
            "chunkSize must be between 1 and ${FileTransferResourceLimits.MAX_CHUNK_SIZE}: $chunkSize"
        }
    }

    private val filesChunks = mutableListOf<FilesChunk>()

    private var filesChunkBuilder = FilesChunkBuilder(chunkSize)

    private var totalSize = 0L

    fun addFile(
        filePath: Path,
        size: Long,
    ) {
        require(size >= 0) { "file size must be non-negative: $size" }
        require(size <= FileTransferResourceLimits.MAX_TOTAL_SIZE - totalSize) {
            "total file size exceeds ${FileTransferResourceLimits.MAX_TOTAL_SIZE} bytes"
        }
        val newTotalSize = totalSize + size
        val requiredChunkCount =
            if (newTotalSize == 0L) {
                1L
            } else {
                ((newTotalSize - 1) / chunkSize) + 1
            }
        require(requiredChunkCount <= FileTransferResourceLimits.MAX_CHUNK_COUNT) {
            "file index requires more than ${FileTransferResourceLimits.MAX_CHUNK_COUNT} chunks"
        }
        totalSize = newTotalSize

        if (size > 0 && filesChunkBuilder.isFull()) {
            filesChunks.add(filesChunkBuilder.build())
            filesChunkBuilder = FilesChunkBuilder(chunkSize)
        }

        var remainingSize = size
        do {
            remainingSize = filesChunkBuilder.addFile(filePath, remainingSize, size)
            if (remainingSize > 0) {
                filesChunks.add(filesChunkBuilder.build())
                filesChunkBuilder = FilesChunkBuilder(chunkSize)
            }
        } while (remainingSize > 0)
    }

    fun build(): FilesIndex {
        if (filesChunkBuilder.isNotEmpty()) {
            filesChunks.add(filesChunkBuilder.build())
        }
        return FilesIndex(filesChunks.toList())
    }
}

class FilesIndex(
    private val filesChunks: List<FilesChunk>,
) {

    fun getChunk(chunkIndex: Int): FilesChunk? = filesChunks.getOrNull(chunkIndex)

    fun getChunkCount(): Int = filesChunks.size
}

class FilesChunkBuilder(
    private val chunkSize: Long,
) {

    init {
        require(chunkSize in 1..FileTransferResourceLimits.MAX_CHUNK_SIZE) {
            "chunkSize must be between 1 and ${FileTransferResourceLimits.MAX_CHUNK_SIZE}: $chunkSize"
        }
    }

    private val fileChunks = mutableListOf<FileChunk>()

    private var remainingChunkSize = chunkSize

    fun addFile(
        filePath: Path,
        remainingSize: Long,
        size: Long,
    ): Long {
        require(size >= 0) { "file size must be non-negative: $size" }
        require(remainingSize in 0..size) {
            "remainingSize must be between 0 and file size: remaining=$remainingSize size=$size"
        }
        check(remainingChunkSize in 0..chunkSize) {
            "remaining chunk size is invalid: $remainingChunkSize"
        }
        val addNewChunkSize = if (remainingSize > remainingChunkSize) remainingChunkSize else remainingSize
        fileChunks.add(FileChunk(size - remainingSize, addNewChunkSize, filePath))
        remainingChunkSize -= addNewChunkSize
        return remainingSize - addNewChunkSize
    }

    fun isNotEmpty(): Boolean = fileChunks.isNotEmpty()

    fun isFull(): Boolean = remainingChunkSize == 0L

    fun build(): FilesChunk = FilesChunk(fileChunks.toList())
}

data class FilesChunk(
    val fileChunks: List<FileChunk>,
) {
    init {
        var totalSize = 0L
        fileChunks.forEach { fileChunk ->
            require(fileChunk.size <= FileTransferResourceLimits.MAX_CHUNK_SIZE - totalSize) {
                "files chunk exceeds ${FileTransferResourceLimits.MAX_CHUNK_SIZE} bytes"
            }
            totalSize += fileChunk.size
        }
    }

    override fun toString(): String {
        val fileChunksToString = fileChunks.joinToString(", ") { it.toString() }
        return "FilesChunk(chunks: [$fileChunksToString])"
    }
}

data class FileChunk(
    val offset: Long,
    val size: Long,
    val path: Path,
) {
    init {
        require(offset >= 0) { "file chunk offset must be non-negative: $offset" }
        require(size in 0..FileTransferResourceLimits.MAX_CHUNK_SIZE) {
            "file chunk size must be between 0 and ${FileTransferResourceLimits.MAX_CHUNK_SIZE}: $size"
        }
        require(offset <= Long.MAX_VALUE - size) { "file chunk range exceeds Long.MAX_VALUE" }
    }

    override fun toString(): String = "FileChunk(path: ${path.name}, offset: $offset, size: $size)"
}
