package com.crosspaste.serializer

import com.crosspaste.utils.getCodecsUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.signal.libsignal.protocol.IdentityKey

object IdentityKeySerializer : KSerializer<IdentityKey> {

    private val codecsUtils = getCodecsUtils()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IdentityKey") {}

    override fun deserialize(decoder: Decoder): IdentityKey {
        val byteArray = codecsUtils.base64Decode(decoder.decodeString())
        return IdentityKey(byteArray)
    }

    override fun serialize(
        encoder: Encoder,
        value: IdentityKey,
    ) {
        encoder.encodeString(codecsUtils.base64Encode(value.serialize()))
    }
}
