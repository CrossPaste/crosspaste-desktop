package com.crosspaste.realm.signal

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class PasteSession : RealmObject {
    @PrimaryKey
    var appInstanceId: String = ""
    var sessionRecord: ByteArray = ByteArray(0)

    constructor()

    constructor(appInstanceId: String, sessionRecord: ByteArray) {
        this.appInstanceId = appInstanceId
        this.sessionRecord = sessionRecord
    }
}
