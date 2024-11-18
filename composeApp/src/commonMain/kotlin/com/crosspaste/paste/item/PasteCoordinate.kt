package com.crosspaste.paste.item

import com.crosspaste.presist.FileInfoTree
import io.realm.kotlin.types.RealmInstant
import okio.Path

open class PasteCoordinate(
    open val appInstanceId: String,
    open val pasteId: Long,
    open val createTime: RealmInstant = RealmInstant.now(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PasteCoordinate

        if (pasteId != other.pasteId) return false
        if (appInstanceId != other.appInstanceId) return false
        if (createTime != other.createTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pasteId.hashCode()
        result = 31 * result + appInstanceId.hashCode()
        result = 31 * result + createTime.hashCode()
        return result
    }
}

open class PasteFileCoordinate(
    override val appInstanceId: String,
    override val pasteId: Long,
    override val createTime: RealmInstant = RealmInstant.now(),
    open val filePath: Path,
) : PasteCoordinate(appInstanceId, pasteId, createTime) {

    constructor(pasteCoordinate: PasteCoordinate, filePath: Path) : this(
        pasteCoordinate.appInstanceId,
        pasteCoordinate.pasteId,
        pasteCoordinate.createTime,
        filePath,
    )

    fun toPasteCoordinate(): PasteCoordinate {
        return PasteCoordinate(appInstanceId, pasteId, createTime)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as PasteFileCoordinate

        if (pasteId != other.pasteId) return false
        if (appInstanceId != other.appInstanceId) return false
        if (createTime != other.createTime) return false
        if (filePath != other.filePath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + pasteId.hashCode()
        result = 31 * result + appInstanceId.hashCode()
        result = 31 * result + createTime.hashCode()
        result = 31 * result + filePath.hashCode()
        return result
    }
}

class PasteFileInfoTreeCoordinate(
    override val appInstanceId: String,
    override val pasteId: Long,
    override val createTime: RealmInstant = RealmInstant.now(),
    override val filePath: Path,
    val fileInfoTree: FileInfoTree,
) : PasteFileCoordinate(appInstanceId, pasteId, createTime, filePath) {

    constructor(pasteCoordinate: PasteCoordinate, filePath: Path, fileInfoTree: FileInfoTree) : this(
        pasteCoordinate.appInstanceId,
        pasteCoordinate.pasteId,
        pasteCoordinate.createTime,
        filePath,
        fileInfoTree,
    )

    fun toPasteFileCoordinate(): PasteFileCoordinate {
        return PasteFileCoordinate(appInstanceId, pasteId, createTime, filePath)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as PasteFileInfoTreeCoordinate

        if (pasteId != other.pasteId) return false
        if (appInstanceId != other.appInstanceId) return false
        if (createTime != other.createTime) return false
        if (filePath != other.filePath) return false
        if (fileInfoTree != other.fileInfoTree) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + pasteId.hashCode()
        result = 31 * result + appInstanceId.hashCode()
        result = 31 * result + createTime.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + fileInfoTree.hashCode()
        return result
    }
}
