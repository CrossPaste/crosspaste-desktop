package com.clipevery.utils

import com.clipevery.clip.item.FilesClipItem
import com.clipevery.clip.item.HtmlClipItem
import com.clipevery.clip.item.ImagesClipItem
import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.serializer.Base64ByteArraySerializer
import com.clipevery.serializer.IdentityKeySerializer
import com.clipevery.serializer.PreKeyBundleSerializer
import io.realm.kotlin.serializers.MutableRealmIntKSerializer
import io.realm.kotlin.serializers.RealmAnyKSerializer
import io.realm.kotlin.serializers.RealmInstantKSerializer
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.modules.subclass
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.state.PreKeyBundle

@Suppress("UNCHECKED_CAST")
object JsonUtils {

    val JSON: Json = Json {
        serializersModule = SerializersModule {
            serializersModuleOf(ByteArray::class, Base64ByteArraySerializer)
            serializersModuleOf(PreKeyBundle::class, PreKeyBundleSerializer)
            serializersModuleOf(IdentityKey::class, IdentityKeySerializer)
            polymorphicDefaultSerializer(RealmList::class) { it: RealmList<*> ->
                if (it.isEmpty() || it[0] is String) {
                    RealmListKSerializer(String.serializer()) as SerializationStrategy<RealmList<*>>
                } else {
                    RealmListKSerializer(RealmAnyKSerializer) as SerializationStrategy<RealmList<*>>
                }
            }
            polymorphic(RealmObject::class) {
                subclass(FilesClipItem::class)
                subclass(HtmlClipItem::class)
                subclass(ImagesClipItem::class)
                subclass(TextClipItem::class)
                subclass(UrlClipItem::class)
            }
            serializersModuleOf(MutableRealmIntKSerializer)
            serializersModuleOf(RealmAnyKSerializer)
            serializersModuleOf(RealmInstantKSerializer)
        }
    }

}