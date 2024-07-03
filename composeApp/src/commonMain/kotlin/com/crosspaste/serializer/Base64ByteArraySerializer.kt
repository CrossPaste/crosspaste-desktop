package com.crosspaste.serializer

import com.crosspaste.utils.getEncryptUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object Base64ByteArraySerializer : KSerializer<ByteArray> {

    private val encryptUtils = getEncryptUtils()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ByteArray") {}

    override fun deserialize(decoder: Decoder): ByteArray {
        return encryptUtils.base64Decode(decoder.decodeString())
    }

    override fun serialize(
        encoder: Encoder,
        value: ByteArray,
    ) {
        encoder.encodeString(encryptUtils.base64Encode(value))
    }
}
