package com.clipevery.presist

import java.nio.file.Path

class FilesIndexBuilder(private val chunkSize: Long) {

    private val filesChunks = mutableListOf<FilesChunk>()

    private var filesChunkBuilder = FilesChunkBuilder(chunkSize)

    fun addFile(
        filePath: Path,
        size: Long,
    ) {
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

class FilesIndex(private val filesChunks: List<FilesChunk>) {

    fun getChunk(chunkIndex: Int): FilesChunk? {
        return filesChunks.getOrNull(chunkIndex)
    }

    fun getChunkCount(): Int {
        return filesChunks.size
    }
}

class FilesChunkBuilder(private val chunkSize: Long) {

    private val fileChunks = mutableListOf<FileChunk>()

    fun addFile(
        filePath: Path,
        remainingSize: Long,
        size: Long,
    ): Long {
        val chunkSize = if (remainingSize > chunkSize) chunkSize else remainingSize
        fileChunks.add(FileChunk(size - remainingSize, chunkSize, filePath))
        return remainingSize - chunkSize
    }

    fun isNotEmpty(): Boolean {
        return fileChunks.isNotEmpty()
    }

    fun build(): FilesChunk {
        return FilesChunk(fileChunks.toList())
    }
}

data class FilesChunk(val fileChunks: List<FileChunk>) {
    override fun toString(): String {
        val fileChunksToString = fileChunks.map { return it.toString() }.joinToString { ", " }
        return "FilesChunk(chunks: [$fileChunksToString])"
    }
}

data class FileChunk(val offset: Long, val size: Long, val path: Path) {
    fun getEndOffset(): Long {
        return offset + size
    }

    override fun toString(): String {
        return "FileChunk(path: ${path.fileName}, offset: $offset, size: $size)"
    }
}
