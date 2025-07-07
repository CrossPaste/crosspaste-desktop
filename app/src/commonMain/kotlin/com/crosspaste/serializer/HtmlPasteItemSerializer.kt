package com.crosspaste.serializer

import com.crosspaste.paste.item.HtmlPasteItem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class HtmlPasteItemSerializer : KSerializer<HtmlPasteItem> {

    override val descriptor =
        buildClassSerialDescriptor("html") {
            element<List<String>>("identifiers")
            element<String>("hash")
            element<String>("html")
            element<Long>("size")
            element<String?>("extraInfo")
            element<String>("relativePath", isOptional = true)
        }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): HtmlPasteItem {
        val dec = decoder.beginStructure(descriptor)
        var identifiers: List<String> = listOf()
        var hash = ""
        var html = ""
        var size = 0L
        var extraInfo: JsonObject? = null

        loop@ while (true) {
            val index = dec.decodeElementIndex(descriptor)
            when (index) {
                0 -> identifiers = dec.decodeSerializableElement(descriptor, 0, ListSerializer(String.serializer()))
                1 -> hash = dec.decodeStringElement(descriptor, 1)
                2 -> html = dec.decodeStringElement(descriptor, 2)
                3 -> size = dec.decodeLongElement(descriptor, 3)
                4 -> {
                    dec.decodeNullableSerializableElement(
                        descriptor,
                        4,
                        JsonElement.serializer(),
                    )?.let { jsonElement ->
                        when (jsonElement) {
                            is JsonObject -> {
                                extraInfo = jsonElement
                            }
                            is JsonPrimitive -> {
                                if (jsonElement != JsonNull) {
                                    runCatching {
                                        extraInfo = parseToJsonElement(jsonElement.content).jsonObject
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> {
                    dec.decodeNullableSerializableElement(
                        descriptor,
                        index,
                        JsonElement.serializer(),
                    )
                }
            }
        }

        dec.endStructure(descriptor)

        return HtmlPasteItem(
            identifiers = identifiers,
            hash = hash,
            html = html,
            size = size,
            extraInfo = extraInfo,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: HtmlPasteItem,
    ) {
        val enc = encoder.beginStructure(descriptor)
        enc.encodeSerializableElement(descriptor, 0, ListSerializer(String.serializer()), value.identifiers)
        enc.encodeStringElement(descriptor, 1, value.hash)
        enc.encodeStringElement(descriptor, 2, value.html)
        enc.encodeLongElement(descriptor, 3, value.size)
        enc.encodeNullableSerializableElement(descriptor, 4, String.serializer(), value.extraInfo.toString())
        // To be compatible with older versions, we must set this field
        enc.encodeStringElement(descriptor, 5, value.relativePath ?: "")
        enc.endStructure(descriptor)
    }
}
