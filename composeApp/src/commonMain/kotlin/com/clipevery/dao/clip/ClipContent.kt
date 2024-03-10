@file:UseSerializers(
    MutableRealmIntKSerializer::class,
    RealmAnyKSerializer::class,
    RealmDictionaryKSerializer::class,
    RealmInstantKSerializer::class,
    RealmListKSerializer::class,
    RealmSetKSerializer::class,
    RealmUUIDKSerializer::class
)

package com.clipevery.dao.clip

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.serializers.MutableRealmIntKSerializer
import io.realm.kotlin.serializers.RealmAnyKSerializer
import io.realm.kotlin.serializers.RealmDictionaryKSerializer
import io.realm.kotlin.serializers.RealmInstantKSerializer
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.serializers.RealmSetKSerializer
import io.realm.kotlin.serializers.RealmUUIDKSerializer
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.io.IOException

@Serializable
class ClipContent: RealmObject {

    var clipAppearItems: RealmList<RealmAny?> = realmListOf()

    @Throws(IOException::class)
    fun clear(realm: MutableRealm, clearResource: Boolean = true) {
        val iterator = clipAppearItems.iterator()
        while(iterator.hasNext()) {
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

