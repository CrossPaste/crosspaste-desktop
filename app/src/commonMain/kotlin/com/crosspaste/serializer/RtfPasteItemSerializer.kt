package com.crosspaste.serializer

import com.crosspaste.paste.item.RtfPasteItem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject

class RtfPasteItemSerializer : KSerializer<RtfPasteItem> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("RtfPasteItem") {
            element<List<String>>("identifiers")
            element<String>("hash")
            element<String>("rtf")
            element<Long>("size")
            element<JsonObject?>("extraInfo")
            element<String>("relativePath")
        }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): RtfPasteItem {
        val dec = decoder.beginStructure(descriptor)
        var identifiers: List<String> = listOf()
        var hash = ""
        var rtf = ""
        var size = 0L
        var extraInfo: JsonObject? = null

        loop@ while (true) {
            when (dec.decodeElementIndex(descriptor)) {
                0 -> identifiers = dec.decodeSerializableElement(descriptor, 0, ListSerializer(String.serializer()))
                1 -> hash = dec.decodeStringElement(descriptor, 1)
                2 -> rtf = dec.decodeStringElement(descriptor, 2)
                3 -> size = dec.decodeLongElement(descriptor, 3)
                4 -> extraInfo = dec.decodeNullableSerializableElement(descriptor, 4, JsonObject.serializer())
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> {
                    dec.decodeElementIndex(descriptor)
                }
            }
        }

        dec.endStructure(descriptor)

        return RtfPasteItem(
            identifiers = identifiers,
            hash = hash,
            rtf = rtf,
            size = size,
            extraInfo = extraInfo,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: RtfPasteItem,
    ) {
        val enc = encoder.beginStructure(descriptor)
        enc.encodeSerializableElement(descriptor, 0, ListSerializer(String.serializer()), value.identifiers)
        enc.encodeStringElement(descriptor, 1, value.hash)
        enc.encodeStringElement(descriptor, 2, value.rtf)
        enc.encodeLongElement(descriptor, 3, value.size)
        enc.encodeNullableSerializableElement(descriptor, 4, JsonObject.serializer(), value.extraInfo)
        // To be compatible with older versions, we must set this field
        enc.encodeStringElement(descriptor, 5, "")
        enc.endStructure(descriptor)
    }
}
