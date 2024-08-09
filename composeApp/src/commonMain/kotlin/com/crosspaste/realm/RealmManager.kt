package com.crosspaste.realm

import io.realm.kotlin.Realm
import okio.Path

interface RealmManager {

    val realm: Realm

    fun writeCopyTo(path: Path)
}
