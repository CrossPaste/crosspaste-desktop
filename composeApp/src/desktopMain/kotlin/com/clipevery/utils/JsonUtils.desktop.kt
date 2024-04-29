package com.clipevery.utils

import com.clipevery.clip.item.FilesClipItem
import com.clipevery.clip.item.HtmlClipItem
import com.clipevery.clip.item.ImagesClipItem
import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipLabel
import com.clipevery.dao.task.ClipTaskExtraInfo
import com.clipevery.presist.DirFileInfoTree
import com.clipevery.presist.FileInfoTree
import com.clipevery.presist.SingleFileInfoTree
import com.clipevery.serializer.Base64ByteArraySerializer
import com.clipevery.serializer.IdentityKeySerializer
import com.clipevery.serializer.PreKeyBundleSerializer
import com.clipevery.task.extra.BaseExtraInfo
import com.clipevery.task.extra.PullExtraInfo
import com.clipevery.task.extra.SyncExtraInfo
import io.realm.kotlin.serializers.MutableRealmIntKSerializer
import io.realm.kotlin.serializers.RealmAnyKSerializer
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.modules.subclass
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.state.PreKeyBundle

actual fun getJsonUtils(): JsonUtils {
    return DesktopJsonUtils
}

object DesktopJsonUtils : JsonUtils {

    override val JSON: Json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            serializersModule =
                SerializersModule {
                    // use in http request
                    serializersModuleOf(ByteArray::class, Base64ByteArraySerializer)
                    serializersModuleOf(PreKeyBundle::class, PreKeyBundleSerializer)
                    serializersModuleOf(IdentityKey::class, IdentityKeySerializer)

                    // use in clip data
                    serializersModuleOf(MutableRealmIntKSerializer)
                    serializersModuleOf(RealmAnyKSerializer)
                    polymorphic(RealmObject::class) {
                        subclass(FilesClipItem::class)
                        subclass(HtmlClipItem::class)
                        subclass(ImagesClipItem::class)
                        subclass(TextClipItem::class)
                        subclass(UrlClipItem::class)
                        subclass(ClipLabel::class)
                        subclass(ClipContent::class)
                    }

                    polymorphic(FileInfoTree::class) {
                        subclass(SingleFileInfoTree::class)
                        subclass(DirFileInfoTree::class)
                    }

                    // use in clip task
                    polymorphic(ClipTaskExtraInfo::class) {
                        subclass(BaseExtraInfo::class)
                        subclass(SyncExtraInfo::class)
                        subclass(PullExtraInfo::class)
                    }
                }
        }
}
