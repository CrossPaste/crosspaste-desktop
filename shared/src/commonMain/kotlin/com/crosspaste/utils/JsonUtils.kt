package com.crosspaste.utils

import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTaskExtraInfo
import com.crosspaste.db.task.PullExtraInfo
import com.crosspaste.db.task.SwitchLanguageInfo
import com.crosspaste.db.task.SyncExtraInfo
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.presist.DirFileInfoTree
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.serializer.Base64ByteArraySerializer
import com.crosspaste.serializer.HtmlPasteItemSerializer
import com.crosspaste.serializer.RtfPasteItemSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.modules.subclass

expect fun getJsonUtils(): JsonUtils

interface JsonUtils {

    val JSON: Json

    fun createJSON(): Json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            serializersModule =
                SerializersModule {
                    // use in http request
                    serializersModuleOf(ByteArray::class, Base64ByteArraySerializer())

                    // use in paste data
                    polymorphic(PasteItem::class) {
                        subclass(ColorPasteItem::class)
                        subclass(FilesPasteItem::class)
                        subclass(HtmlPasteItem::class, HtmlPasteItemSerializer())
                        subclass(ImagesPasteItem::class)
                        subclass(RtfPasteItem::class, RtfPasteItemSerializer())
                        subclass(TextPasteItem::class)
                        subclass(UrlPasteItem::class)
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
                        subclass(SwitchLanguageInfo::class)
                    }

                    extSerializerModule()
                }
        }

    fun SerializersModuleBuilder.extSerializerModule() { }
}
