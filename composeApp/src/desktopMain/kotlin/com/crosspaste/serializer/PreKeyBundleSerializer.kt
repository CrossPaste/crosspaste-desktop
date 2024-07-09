package com.crosspaste.serializer

import com.crosspaste.utils.getCodecsUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.PreKeyBundle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

object PreKeyBundleSerializer : KSerializer<PreKeyBundle> {

    private val codecsUtils = getCodecsUtils()

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("PreKeyBundle") {
        }

    override fun serialize(
        encoder: Encoder,
        value: PreKeyBundle,
    ) {
        val byteArray = encodePreKeyBundle(value)
        encoder.encodeString(codecsUtils.base64Encode(byteArray))
    }

    override fun deserialize(decoder: Decoder): PreKeyBundle {
        val byteArray = codecsUtils.base64Decode(decoder.decodeString())
        return decodePreKeyBundle(byteArray)
    }

    @Throws(IOException::class, InvalidKeyException::class)
    fun decodePreKeyBundle(encoded: ByteArray): PreKeyBundle {
        val byteStream = ByteArrayInputStream(encoded)
        val dataStream = DataInputStream(byteStream)
        val registrationId = dataStream.readInt()
        val deviceId = dataStream.readInt()
        val preKeyId = dataStream.readInt()
        val preKeyPublicSize = dataStream.readInt()
        val preKeyPublicBytes = ByteArray(preKeyPublicSize)
        dataStream.read(preKeyPublicBytes)
        val preKeyPublic = ECPublicKey(preKeyPublicBytes)
        val signedPreKeyId = dataStream.readInt()

        val signedPreKeyPublicSize = dataStream.readInt()
        val signedPreKeyPublicBytes = ByteArray(signedPreKeyPublicSize)
        dataStream.read(signedPreKeyPublicBytes)
        val signedPreKeyPublic = ECPublicKey(signedPreKeyPublicBytes)

        val signedPreKeySignatureSize = dataStream.readInt()
        val signedPreKeySignatureBytes = ByteArray(signedPreKeySignatureSize)
        dataStream.read(signedPreKeySignatureBytes)

        val identityKeySize = dataStream.readInt()
        val identityKeyBytes = ByteArray(identityKeySize)
        dataStream.read(identityKeyBytes)
        val identityKey = IdentityKey(ECPublicKey(identityKeyBytes))

        return PreKeyBundle(
            registrationId,
            deviceId,
            preKeyId,
            preKeyPublic,
            signedPreKeyId,
            signedPreKeyPublic,
            signedPreKeySignatureBytes,
            identityKey,
        )
    }

    fun encodePreKeyBundle(preKeyBundle: PreKeyBundle): ByteArray {
        val byteStream = ByteArrayOutputStream()
        val dataStream = DataOutputStream(byteStream)
        dataStream.writeInt(preKeyBundle.registrationId)
        dataStream.writeInt(preKeyBundle.deviceId)
        dataStream.writeInt(preKeyBundle.preKeyId)
        val preKeyPublicBytes = preKeyBundle.preKey.serialize()
        dataStream.writeInt(preKeyPublicBytes.size)
        dataStream.write(preKeyPublicBytes)
        dataStream.writeInt(preKeyBundle.signedPreKeyId)
        val signedPreKeySignatureBytes = preKeyBundle.signedPreKey.serialize()
        dataStream.writeInt(signedPreKeySignatureBytes.size)
        dataStream.write(signedPreKeySignatureBytes)
        dataStream.writeInt(preKeyBundle.signedPreKeySignature.size)
        dataStream.write(preKeyBundle.signedPreKeySignature)
        val identityKeyBytes = preKeyBundle.identityKey.serialize()
        dataStream.writeInt(identityKeyBytes.size)
        dataStream.write(identityKeyBytes)
        return byteStream.toByteArray()
    }
}
