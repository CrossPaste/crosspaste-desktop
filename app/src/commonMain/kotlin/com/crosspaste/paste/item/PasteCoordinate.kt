package com.crosspaste.paste.item

import com.crosspaste.presist.FileInfoTree
import com.crosspaste.utils.DateUtils
import okio.Path

open class PasteCoordinate(
    open val id: Long,
    open val appInstanceId: String,
    open val createTime: Long = DateUtils.nowEpochMilliseconds(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasteCoordinate) return false

        if (id != other.id) return false
        if (appInstanceId != other.appInstanceId) return false
        if (createTime != other.createTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + appInstanceId.hashCode()
        result = 31 * result + createTime.hashCode()
        return result
    }
}

open class PasteFileCoordinate(
    override val id: Long,
    override val appInstanceId: String,
    override val createTime: Long = DateUtils.nowEpochMilliseconds(),
    open val filePath: Path,
) : PasteCoordinate(id, appInstanceId, createTime) {

    constructor(pasteCoordinate: PasteCoordinate, filePath: Path) : this(
        pasteCoordinate.id,
        pasteCoordinate.appInstanceId,
        pasteCoordinate.createTime,
        filePath,
    )

    fun toPasteCoordinate(): PasteCoordinate = PasteCoordinate(id, appInstanceId, createTime)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasteFileCoordinate) return false
        if (!super.equals(other)) return false

        if (id != other.id) return false
        if (appInstanceId != other.appInstanceId) return false
        if (createTime != other.createTime) return false
        if (filePath != other.filePath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + appInstanceId.hashCode()
        result = 31 * result + createTime.hashCode()
        result = 31 * result + filePath.hashCode()
        return result
    }
}

class PasteFileInfoTreeCoordinate(
    override val id: Long,
    override val appInstanceId: String,
    override val createTime: Long = DateUtils.nowEpochMilliseconds(),
    override val filePath: Path,
    val fileInfoTree: FileInfoTree,
) : PasteFileCoordinate(id, appInstanceId, createTime, filePath) {

    constructor(pasteCoordinate: PasteCoordinate, filePath: Path, fileInfoTree: FileInfoTree) : this(
        pasteCoordinate.id,
        pasteCoordinate.appInstanceId,
        pasteCoordinate.createTime,
        filePath,
        fileInfoTree,
    )

    fun toPasteFileCoordinate(): PasteFileCoordinate = PasteFileCoordinate(id, appInstanceId, createTime, filePath)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasteFileInfoTreeCoordinate) return false
        if (!super.equals(other)) return false

        if (id != other.id) return false
        if (appInstanceId != other.appInstanceId) return false
        if (createTime != other.createTime) return false
        if (filePath != other.filePath) return false
        if (fileInfoTree != other.fileInfoTree) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + appInstanceId.hashCode()
        result = 31 * result + createTime.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + fileInfoTree.hashCode()
        return result
    }
}
