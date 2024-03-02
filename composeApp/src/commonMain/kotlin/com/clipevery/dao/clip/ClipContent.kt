package com.clipevery.dao.clip

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import java.io.IOException

class ClipContent: RealmObject {

    var clipAppearItems: RealmList<RealmAny?> = realmListOf()

    constructor()

    constructor(clipAppearItems: RealmList<RealmAny?>) {
        this.clipAppearItems = clipAppearItems
    }

    @Throws(IOException::class)
    fun clear(realm: MutableRealm) {
        val iterator = clipAppearItems.iterator()
        while(iterator.hasNext()) {
            val clipAppearItem = iterator.next()
            iterator.remove()
            getClipItem(clipAppearItem)?.clear(realm)
        }
        realm.delete(this)
    }

    companion object {
        fun getClipItem(anyValue: RealmAny?): ClipAppearItem? {
            return anyValue?.let {
                return when (it.type) {
                    RealmAny.Type.OBJECT -> {
                        val asRealmObject = anyValue.asRealmObject<RealmObject>()
                        if (asRealmObject is ClipAppearItem) {
                            asRealmObject
                        } else {
                            null
                        }
                    }
                    else -> null
                }
            }
        }
    }
}

