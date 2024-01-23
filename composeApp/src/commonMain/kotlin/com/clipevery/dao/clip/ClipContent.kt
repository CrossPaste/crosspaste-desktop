package com.clipevery.dao.clip

import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import java.io.IOException

class ClipContent: RealmObject {

    private var clipAppearItems: RealmList<RealmAny?> = realmListOf()

    constructor()

    constructor(clipAppearItems: RealmList<RealmAny?>) {
        this.clipAppearItems = clipAppearItems
    }

    @Throws(IOException::class)
    fun clear() {
        for (clipAppearItem in clipAppearItems) {
            getClipItem(clipAppearItem)?.clear()
        }
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

