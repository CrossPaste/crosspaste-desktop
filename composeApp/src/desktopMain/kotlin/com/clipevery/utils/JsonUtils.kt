package com.clipevery.utils

import com.clipevery.clip.item.FilesClipItem
import com.clipevery.clip.item.HtmlClipItem
import com.clipevery.clip.item.ImagesClipItem
import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipLabel
import com.clipevery.dao.task.ClipTaskExtraInfo
import com.clipevery.serializer.Base64ByteArraySerializer
import com.clipevery.serializer.IdentityKeySerializer
import com.clipevery.serializer.PreKeyBundleSerializer
import com.clipevery.serializer.RealmInstantSerializer
import com.clipevery.task.extra.BaseExtraInfo
import com.clipevery.task.extra.SyncExtraInfo
import io.realm.kotlin.serializers.MutableRealmIntKSerializer
import io.realm.kotlin.serializers.RealmAnyKSerializer
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.serializers.RealmSetKSerializer
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
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
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            // use in http request
            serializersModuleOf(ByteArray::class, Base64ByteArraySerializer)
            serializersModuleOf(PreKeyBundle::class, PreKeyBundleSerializer)
            serializersModuleOf(IdentityKey::class, IdentityKeySerializer)

            // use in clip data
            serializersModuleOf(MutableRealmIntKSerializer)
            serializersModuleOf(RealmAnyKSerializer)
            serializersModuleOf(RealmInstantSerializer)
            polymorphic(RealmObject::class) {
                subclass(FilesClipItem::class)
                subclass(HtmlClipItem::class)
                subclass(ImagesClipItem::class)
                subclass(TextClipItem::class)
                subclass(UrlClipItem::class)
                subclass(ClipLabel::class)
                subclass(ClipData::class)
                subclass(ClipContent::class)
            }
            polymorphicDefaultSerializer(RealmList::class) { it: RealmList<*> ->
                if (it.isEmpty() || it[0] is String) {
                    RealmListKSerializer(String.serializer()) as SerializationStrategy<RealmList<*>>
                } else if (it[0] is RealmAny?) {
                    RealmListKSerializer(RealmAnyKSerializer) as SerializationStrategy<RealmList<*>>
                } else {
                    throw IllegalArgumentException("Unsupported RealmList type: ${it[0]}")
                }
            }
            polymorphicDefaultSerializer(RealmSet::class) { it: RealmSet<*> ->
                if (it.isEmpty() || it.first() is ClipLabel) {
                    RealmSetKSerializer(ClipLabel.serializer()) as SerializationStrategy<RealmSet<*>>
                } else {
                    throw IllegalArgumentException("Unsupported RealmSet type: ${it.first()}")
                }
            }

            // use in clip task
            polymorphic(ClipTaskExtraInfo::class) {
                subclass(BaseExtraInfo::class)
                subclass(SyncExtraInfo::class)
            }
        }
    }

}