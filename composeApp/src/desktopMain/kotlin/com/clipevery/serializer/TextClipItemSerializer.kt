package com.clipevery.serializer

import com.clipevery.clip.item.TextClipItem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure

object TextClipItemSerializer : KSerializer<TextClipItem> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("TextClipItem") {
            element<String>("identifier")
            element<String>("text")
            element<Boolean>("favorite")
            element<Long>("size")
            element<String>("md5")
            element<String?>("extraInfo")
        }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): TextClipItem {
        val dec = decoder.beginStructure(descriptor)
        var identifier = ""
        var text = ""
        var favorite = false
        var size = 0L
        var md5 = ""
        var extraInfo: String? = null
        loop@ while (true) {
            when (val index = dec.decodeElementIndex(descriptor)) {
                0 -> identifier = dec.decodeStringElement(descriptor, index)
                1 -> text = dec.decodeStringElement(descriptor, index)
                2 -> favorite = dec.decodeBooleanElement(descriptor, index)
                3 -> size = dec.decodeLongElement(descriptor, index)
                4 -> md5 = dec.decodeStringElement(descriptor, index)
                5 -> extraInfo = dec.decodeNullableSerializableElement(descriptor, index, String.serializer())
                else -> break@loop
            }
        }
        dec.endStructure(descriptor)
        return TextClipItem().apply {
            this.identifier = identifier
            this.text = text
            this.favorite = favorite
            this.size = size
            this.md5 = md5
            this.extraInfo = extraInfo
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: TextClipItem,
    ) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.identifier)
            encodeStringElement(descriptor, 1, value.text)
            encodeBooleanElement(descriptor, 2, value.favorite)
            encodeLongElement(descriptor, 3, value.size)
            encodeStringElement(descriptor, 4, value.md5)
            encodeNullableSerializableElement(descriptor, 5, String.serializer(), value.extraInfo)
        }
    }
}
