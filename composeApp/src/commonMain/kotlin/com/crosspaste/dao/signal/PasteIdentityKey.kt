package com.crosspaste.dao.signal

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class PasteIdentityKey : RealmObject {
    @PrimaryKey
    var appInstanceId: String = ""
    var serialized: ByteArray = ByteArray(0)

    constructor()

    constructor(appInstanceId: String, serialized: ByteArray) {
        this.appInstanceId = appInstanceId
        this.serialized = serialized
    }
}
