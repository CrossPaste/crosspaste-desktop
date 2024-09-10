package com.crosspaste.utils

import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.presist.DirFileInfoTree
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.realm.paste.PasteCollection
import com.crosspaste.realm.task.PasteTaskExtraInfo
import com.crosspaste.serializer.Base64ByteArraySerializer
import com.crosspaste.serializer.PreKeyBundleSerializer
import com.crosspaste.signal.PreKeyBundleInterface
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
                    serializersModuleOf(PreKeyBundleInterface::class, PreKeyBundleSerializer)

                    // use in paste data
                    serializersModuleOf(MutableRealmIntKSerializer)
                    serializersModuleOf(RealmAnyKSerializer)
                    polymorphic(RealmObject::class) {
                        subclass(FilesPasteItem::class)
                        subclass(HtmlPasteItem::class)
                        subclass(ImagesPasteItem::class)
                        subclass(TextPasteItem::class)
                        subclass(UrlPasteItem::class)
                        subclass(com.crosspaste.realm.paste.PasteLabel::class)
                        subclass(PasteCollection::class)
                    }

                    polymorphic(FileInfoTree::class) {
                        subclass(SingleFileInfoTree::class)
                        subclass(DirFileInfoTree::class)
                    }

                    // use in paste task
                    polymorphic(PasteTaskExtraInfo::class) {
                        subclass(BaseExtraInfo::class)
                        subclass(SyncExtraInfo::class)
                        subclass(PullExtraInfo::class)
                    }
                }
        }
}
