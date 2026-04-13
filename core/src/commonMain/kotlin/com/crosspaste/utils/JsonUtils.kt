package com.crosspaste.utils

import com.crosspaste.paste.PasteData
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
import com.crosspaste.serializer.PasteDataSerializer
import com.crosspaste.serializer.RtfPasteItemSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
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
                    contextual(ByteArray::class, Base64ByteArraySerializer())
                    contextual(PasteData::class, PasteDataSerializer())

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

                    extSerializerModule()
                }
        }

    fun SerializersModuleBuilder.extSerializerModule() {}
}
