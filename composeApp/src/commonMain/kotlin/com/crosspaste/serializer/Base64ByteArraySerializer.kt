package com.crosspaste.serializer

import com.crosspaste.utils.getCodecsUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class Base64ByteArraySerializer : KSerializer<ByteArray> {

    private val codecsUtils = getCodecsUtils()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ByteArray") {}

    override fun deserialize(decoder: Decoder): ByteArray {
        return codecsUtils.base64Decode(decoder.decodeString())
    }

    override fun serialize(
        encoder: Encoder,
        value: ByteArray,
    ) {
        encoder.encodeString(codecsUtils.base64Encode(value))
    }
}
