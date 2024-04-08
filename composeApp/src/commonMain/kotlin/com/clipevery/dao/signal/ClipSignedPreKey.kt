package com.clipevery.dao.signal

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class ClipSignedPreKey : RealmObject {
    @PrimaryKey
    var id: Int = 0
    var serialized: ByteArray = ByteArray(0)

    constructor()

    constructor(id: Int, serialized: ByteArray) {
        this.id = id
        this.serialized = serialized
    }
}
