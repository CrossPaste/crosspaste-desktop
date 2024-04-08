package com.clipevery.dao.clip

import com.clipevery.serializer.RealmAnyRealmListSerializer
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
@SerialName("content")
class ClipContent : RealmObject {

    @Serializable(with = RealmAnyRealmListSerializer::class)
    var clipAppearItems: RealmList<RealmAny?> = realmListOf()

    @Throws(IOException::class)
    fun clear(
        realm: MutableRealm,
        clearResource: Boolean = true,
    ) {
        val iterator = clipAppearItems.iterator()
        while (iterator.hasNext()) {
            val clipAppearItem = iterator.next()
            iterator.remove()
            getClipItem(clipAppearItem)?.clear(realm, clearResource)
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
