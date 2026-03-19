package com.crosspaste.presist

import com.crosspaste.paste.item.PasteFile
import com.crosspaste.paste.item.PasteFileImpl
import com.crosspaste.utils.getCodecsUtils
import okio.Path

fun FileInfoTree.getPasteFileList(path: Path): List<PasteFile> =
    when (this) {
        is DirFileInfoTree -> {
            val list = mutableListOf<PasteFile>()
            val iter = this.iterator()
            while (iter.hasNext()) {
                val (name, fileTree) = iter.next()
                list.addAll(fileTree.getPasteFileList(path.resolve(name)))
            }
            list
        }
        is SingleFileInfoTree -> listOf(PasteFileImpl(path, hash))
    }

class FileInfoTreeBuilder {

    private val codecsUtils = getCodecsUtils()

    private val tree = mutableMapOf<String, FileInfoTree>()

    private var size = 0L

    private val hashList = mutableListOf<String>()

    fun addFileInfoTree(
        name: String,
        fileInfoTree: FileInfoTree,
    ) {
        tree[name] = fileInfoTree
        size += fileInfoTree.size
        hashList.add(fileInfoTree.hash)
    }

    fun build(path: Path): FileInfoTree {
        val hash =
            if (hashList.isEmpty()) {
                codecsUtils.hashByString(path.name)
            } else {
                codecsUtils.hashByArray(hashList.toTypedArray())
            }
        return DirFileInfoTree(tree, size, hash)
    }
}
