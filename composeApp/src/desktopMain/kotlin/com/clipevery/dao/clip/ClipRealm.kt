package com.clipevery.dao.clip

import io.realm.kotlin.Realm
import io.realm.kotlin.query.Sort

class ClipRealm(private val realm: Realm): ClipDao {
    override fun getMaxClipId(): Int {
        return realm.query(ClipData::class).sort("clipId", Sort.DESCENDING).first().find()?.clipId ?: 0
    }

    override fun createClipData(clipData: ClipData) {
        realm.writeBlocking {
            copyToRealm(clipData)
        }
    }
}