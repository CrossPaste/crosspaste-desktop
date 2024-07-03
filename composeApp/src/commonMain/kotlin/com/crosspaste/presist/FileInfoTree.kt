package com.crosspaste.presist

import com.crosspaste.clip.item.ClipFile
import com.crosspaste.clip.item.ClipFileImpl
import com.crosspaste.utils.getEncryptUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.nio.file.Path
import java.util.TreeMap

interface FileInfoTree {

    val size: Long

    val md5: String

    fun isFile(): Boolean

    fun getClipFileList(path: Path): List<ClipFile>

    fun getCount(): Long
}

@Serializable
@SerialName("dir")
class DirFileInfoTree(
    private val tree: Map<String, FileInfoTree>,
    override val size: Long,
    override val md5: String,
) : FileInfoTree {
    @Transient
    private val sortTree: TreeMap<String, FileInfoTree> = TreeMap(tree)

    fun getTree(): Map<String, FileInfoTree> {
        return sortTree
    }

    override fun isFile(): Boolean {
        return false
    }

    override fun getClipFileList(path: Path): List<ClipFile> {
        return tree.map { (name, fileTree) ->
            fileTree.getClipFileList(path.resolve(name))
        }.flatten()
    }

    override fun getCount(): Long {
        return tree.values.sumOf { it.getCount() }
    }
}

@Serializable
@SerialName("file")
class SingleFileInfoTree(
    override val size: Long,
    override val md5: String,
) : FileInfoTree {

    override fun isFile(): Boolean {
        return true
    }

    override fun getClipFileList(path: Path): List<ClipFile> {
        return listOf(ClipFileImpl(path, md5))
    }

    override fun getCount(): Long {
        return 1L
    }
}

class FileInfoTreeBuilder {

    private val encryptUtils = getEncryptUtils()

    private val tree = mutableMapOf<String, FileInfoTree>()

    private var size = 0L

    private val md5List = mutableListOf<String>()

    fun addFileInfoTree(
        name: String,
        fileInfoTree: FileInfoTree,
    ) {
        tree[name] = fileInfoTree
        size += fileInfoTree.size
        md5List.add(fileInfoTree.md5)
    }

    fun build(path: Path): FileInfoTree {
        val md5 =
            if (md5List.isEmpty()) {
                encryptUtils.md5ByString(path.fileName.toString())
            } else {
                encryptUtils.md5ByArray(md5List.toTypedArray())
            }
        return DirFileInfoTree(tree, size, md5)
    }
}
