package com.crosspaste.realm.signal

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class PastePreKey : RealmObject {
    @PrimaryKey
    var id: Int = 0
    var serialized: ByteArray = ByteArray(0)

    constructor()

    constructor(id: Int, serialized: ByteArray) {
        this.id = id
        this.serialized = serialized
    }
}
