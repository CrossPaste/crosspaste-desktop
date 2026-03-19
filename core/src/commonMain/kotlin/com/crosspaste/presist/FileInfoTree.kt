package com.crosspaste.presist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed interface FileInfoTree {

    val size: Long

    val hash: String

    fun isFile(): Boolean

    fun getCount(): Long
}

@Serializable
@SerialName("dir")
class DirFileInfoTree(
    private val tree: Map<String, FileInfoTree>,
    override val size: Long,
    override val hash: String,
) : FileInfoTree {
    @Transient
    private val sortTree: List<Pair<String, FileInfoTree>> =
        tree.entries
            .map { Pair(it.key, it.value) }
            .sortedBy { it.first }

    fun iterator(): Iterator<Pair<String, FileInfoTree>> = sortTree.iterator()

    override fun isFile(): Boolean = false

    override fun getCount(): Long = tree.values.sumOf { it.getCount() }
}

@Serializable
@SerialName("file")
class SingleFileInfoTree(
    override val size: Long,
    override val hash: String,
) : FileInfoTree {

    override fun isFile(): Boolean = true

    override fun getCount(): Long = 1L
}
