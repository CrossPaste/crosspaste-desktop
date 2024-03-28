package com.clipevery.presist

import com.clipevery.clip.item.ClipFile
import com.clipevery.clip.item.ClipFileImpl
import com.clipevery.utils.EncryptUtils
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
}

@Serializable
@SerialName("dir")
class DirFileInfoTree(private val tree: Map<String, FileInfoTree>,
                      override val size: Long,
                      override val md5: String) : FileInfoTree {
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
}

@Serializable
@SerialName("file")
class SingleFileInfoTree(override val size: Long,
                         override val md5: String) : FileInfoTree {

    override fun isFile(): Boolean {
        return true
    }

    override fun getClipFileList(path: Path): List<ClipFile> {
        return listOf(ClipFileImpl(path, md5))
    }
}

class FileInfoTreeBuilder {

    private val tree = TreeMap<String, FileInfoTree>()

    private var size = 0L

    private val md5List = mutableListOf<String>()

    fun addFileInfoTree(name: String, fileInfoTree: FileInfoTree) {
        tree[name] = fileInfoTree
        size += fileInfoTree.size
        md5List.add(fileInfoTree.md5)
    }

    fun build(path: Path): FileInfoTree {
        val md5 = if (md5List.isEmpty()) {
            EncryptUtils.md5ByString(path.fileName.toString())
        } else {
            EncryptUtils.md5ByArray(md5List.toTypedArray())
        }
        return DirFileInfoTree(tree, size, md5)
    }

}
