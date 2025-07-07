package com.crosspaste.serializer

import com.crosspaste.paste.item.HtmlPasteItem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class HtmlPasteItemSerializer : KSerializer<HtmlPasteItem> {

    private val deserializeDescriptor =
        buildClassSerialDescriptor("html") {
            element<List<String>>("identifiers")
            element<String>("hash")
            element<String>("html")
            element<Long>("size")
            element<JsonElement?>("extraInfo")
        }

    private val serializeDescriptor =
        buildClassSerialDescriptor("html") {
            element<List<String>>("identifiers")
            element<String>("hash")
            element<String>("html")
            element<Long>("size")
            element<String?>("extraInfo")
            element<String>("relativePath")
        }

    override val descriptor: SerialDescriptor = serializeDescriptor

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): HtmlPasteItem {
        val dec = decoder.beginStructure(deserializeDescriptor)
        var identifiers: List<String> = listOf()
        var hash = ""
        var html = ""
        var size = 0L
        var extraInfo: JsonObject? = null

        loop@ while (true) {
            when (dec.decodeElementIndex(deserializeDescriptor)) {
                0 -> identifiers = dec.decodeSerializableElement(deserializeDescriptor, 0, ListSerializer(String.serializer()))
                1 -> hash = dec.decodeStringElement(deserializeDescriptor, 1)
                2 -> html = dec.decodeStringElement(deserializeDescriptor, 2)
                3 -> size = dec.decodeLongElement(deserializeDescriptor, 3)
                4 -> {
                    val jsonElement = dec.decodeNullableSerializableElement(deserializeDescriptor, 4, JsonElement.serializer())
                    when (jsonElement) {
                        is JsonObject -> {
                            extraInfo = jsonElement
                        }
                        is JsonPrimitive -> {
                            extraInfo = parseToJsonElement(jsonElement.content).jsonObject
                        }
                        else -> {}
                    }
                }
                else -> {
                    break@loop
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
        val enc = encoder.beginStructure(serializeDescriptor)
        enc.encodeSerializableElement(serializeDescriptor, 0, ListSerializer(String.serializer()), value.identifiers)
        enc.encodeStringElement(serializeDescriptor, 1, value.hash)
        enc.encodeStringElement(serializeDescriptor, 2, value.html)
        enc.encodeLongElement(serializeDescriptor, 3, value.size)
        enc.encodeNullableSerializableElement(serializeDescriptor, 4, String.serializer(), value.extraInfo.toString())
        // To be compatible with older versions, we must set this field
        enc.encodeStringElement(serializeDescriptor, 5, value.relativePath ?: "")
        enc.endStructure(serializeDescriptor)
    }
}
