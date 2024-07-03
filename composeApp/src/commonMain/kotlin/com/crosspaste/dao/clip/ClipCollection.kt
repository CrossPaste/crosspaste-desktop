package com.crosspaste.dao.clip

import com.crosspaste.serializer.RealmAnyRealmListSerializer
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException

@Serializable
@SerialName("collection")
class ClipCollection : RealmObject {

    @Serializable(with = RealmAnyRealmListSerializer::class)
    var clipItems: RealmList<RealmAny?> = realmListOf()

    @Throws(IOException::class)
    fun clear(
        realm: MutableRealm,
        clearResource: Boolean = true,
    ) {
        val iterator = clipItems.iterator()
        while (iterator.hasNext()) {
            val clipItem = iterator.next()
            iterator.remove()
            getClipItem(clipItem)?.clear(realm, clearResource)
        }
        realm.delete(this)
    }

    companion object {
        fun getClipItem(anyValue: RealmAny?): ClipItem? {
            return anyValue?.let {
                return when (it.type) {
                    RealmAny.Type.OBJECT -> {
                        val asRealmObject = anyValue.asRealmObject<RealmObject>()
                        if (asRealmObject is ClipItem) {
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
