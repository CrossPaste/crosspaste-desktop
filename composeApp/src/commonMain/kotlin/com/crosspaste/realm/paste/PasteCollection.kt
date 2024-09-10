package com.crosspaste.realm.paste

import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.serializer.RealmAnyRealmListSerializer
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("collection")
class PasteCollection : RealmObject {

    @Serializable(with = RealmAnyRealmListSerializer::class)
    var pasteItems: RealmList<RealmAny?> = realmListOf()

    fun clear(
        realm: MutableRealm,
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean = true,
    ) {
        val iterator = pasteItems.iterator()
        while (iterator.hasNext()) {
            val pasteItem = iterator.next()
            iterator.remove()
            getPasteItem(pasteItem)?.clear(realm, userDataPathProvider, clearResource)
        }
        realm.delete(this)
    }

    companion object {
        fun getPasteItem(anyValue: RealmAny?): PasteItem? {
            return anyValue?.let {
                return when (it.type) {
                    RealmAny.Type.OBJECT -> {
                        val asRealmObject = anyValue.asRealmObject<RealmObject>()
                        if (asRealmObject is PasteItem) {
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
