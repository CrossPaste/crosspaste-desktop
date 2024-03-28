package com.clipevery.presist

import java.nio.file.Path

class FilesIndexBuilder {
    fun add(fileInfoTreeMap: Map<String, FileInfoTree>) {
        TODO("Not yet implemented")
    }

    fun build(): FilesIndex {
        TODO("Not yet implemented")
    }

    fun addFile(filePath: Path, size: Long) {
        TODO("Not yet implemented")
    }

}

class FilesIndex(private val chunkCount: Int) {
    fun getChunk(chunkIndex: Int): FilesChunk? {
        TODO("Not yet implemented")
    }

    fun getChunkCount(): Int {
        return chunkCount
    }
}

data class FilesChunk(val fileChunks: List<FileChunk>)

data class FileChunk(val offset: Long, val size: Long, val path: Path) {
    fun getEndOffset(): Long {
        return offset + size
    }
}