package com.clipevery.dao.clip

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class ClipResource: RealmObject {
    @PrimaryKey
    var appInstanceId: String = ""
    var imageSize: Long = 0
    var fileSize: Long = 0

}

data class ClipResourceInfo(
    val clipNumber: Long,
    val imageSize: Long,
    val fileSize: Long
)