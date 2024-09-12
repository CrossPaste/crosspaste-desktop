package com.crosspaste.paste.item

import io.realm.kotlin.types.RealmInstant
import okio.Path

data class PasteCoordinate(
    val appInstanceId: String,
    val pasteId: Long,
    val createTime: RealmInstant = RealmInstant.now(),
)

data class PasteFileCoordinate(
    val appInstanceId: String,
    val pasteId: Long,
    val createTime: RealmInstant = RealmInstant.now(),
    val filePath: Path,
) {

    constructor(pasteCoordinate: PasteCoordinate, filePath: Path) : this(
        pasteCoordinate.appInstanceId,
        pasteCoordinate.pasteId,
        pasteCoordinate.createTime,
        filePath,
    )

    fun toPasteCoordinate(): PasteCoordinate {
        return PasteCoordinate(appInstanceId, pasteId, createTime)
    }
}
