package com.crosspaste.utils

import com.crosspaste.clip.item.FilesClipItem
import com.crosspaste.clip.item.HtmlClipItem
import com.crosspaste.clip.item.ImagesClipItem
import com.crosspaste.clip.item.TextClipItem
import com.crosspaste.clip.item.UrlClipItem
import com.crosspaste.dao.clip.ClipCollection
import com.crosspaste.dao.clip.ClipLabel
import com.crosspaste.dao.task.ClipTaskExtraInfo
import com.crosspaste.presist.DirFileInfoTree
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.serializer.Base64ByteArraySerializer
import com.crosspaste.serializer.IdentityKeySerializer
import com.crosspaste.serializer.PreKeyBundleSerializer
import com.crosspaste.task.extra.BaseExtraInfo
import com.crosspaste.task.extra.PullExtraInfo
import com.crosspaste.task.extra.SyncExtraInfo
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
                        subclass(ClipCollection::class)
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
