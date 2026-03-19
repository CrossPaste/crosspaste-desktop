package com.crosspaste.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class Base64ByteArraySerializer : KSerializer<ByteArray> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ByteArray") {}

    override fun deserialize(decoder: Decoder): ByteArray = Base64.decode(decoder.decodeString())

    override fun serialize(
        encoder: Encoder,
        value: ByteArray,
    ) {
        encoder.encodeString(Base64.encode(value))
    }
}
