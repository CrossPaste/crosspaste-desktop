package com.crosspaste.realm.secure

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class CryptPublicKey : RealmObject {
    @PrimaryKey
    var appInstanceId: String = ""
    var serialized: ByteArray = ByteArray(0)

    constructor()

    constructor(appInstanceId: String, serialized: ByteArray) {
        this.appInstanceId = appInstanceId
        this.serialized = serialized
    }
}
