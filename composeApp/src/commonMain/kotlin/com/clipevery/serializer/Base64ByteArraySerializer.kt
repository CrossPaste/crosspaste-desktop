package com.clipevery.serializer

import com.clipevery.utils.EncryptUtils.base64Decode
import com.clipevery.utils.EncryptUtils.base64Encode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object Base64ByteArraySerializer: KSerializer<ByteArray> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ByteArray") {}

    override fun deserialize(decoder: Decoder): ByteArray {
        return base64Decode(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(base64Encode(value))
    }
}