package com.clipevery.serializer

import com.clipevery.utils.EncryptUtils.base64Decode
import com.clipevery.utils.EncryptUtils.base64Encode
import com.clipevery.utils.decodePreKeyBundle
import com.clipevery.utils.encodePreKeyBundle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.signal.libsignal.protocol.state.PreKeyBundle

object PreKeyBundleSerializer : KSerializer<PreKeyBundle> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("PreKeyBundle") {
        }

    override fun serialize(
        encoder: Encoder,
        value: PreKeyBundle,
    ) {
        val byteArray = encodePreKeyBundle(value)
        encoder.encodeString(base64Encode(byteArray))
    }

    override fun deserialize(decoder: Decoder): PreKeyBundle {
        val byteArray = base64Decode(decoder.decodeString())
        return decodePreKeyBundle(byteArray)
    }
}
