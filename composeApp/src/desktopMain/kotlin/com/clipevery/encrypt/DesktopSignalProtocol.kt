package com.clipevery.encrypt

import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.util.KeyHelper
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class DesktopSignalProtocol(override val identityKeyPair: IdentityKeyPair,
                            override val registrationId: Int,
                            override val preKeys: List<PreKeyRecord>,
                            override val signedPreKey: SignedPreKeyRecord
    ): SignalProtocol {

    constructor(): this(KeyHelper.generateIdentityKeyPair(),
        KeyHelper.generateRegistrationId(false),
        KeyHelper.generatePreKeys(0, 5),
        KeyHelper.generateSignedPreKey(KeyHelper.generateIdentityKeyPair(), 5))
}

fun readSignalProtocol(data: ByteArray): SignalProtocol {
    val inputStream = DataInputStream(data.inputStream())
    val identityKeyPairSize = inputStream.readInt()
    val identityKeyPairBytes = inputStream.readNBytes(identityKeyPairSize)
    val identityKeyPair = IdentityKeyPair(identityKeyPairBytes)
    val registrationId = inputStream.readInt()
    val preKeysSize = inputStream.readInt()
    val preKeys = buildList {
        for (i in 0 until preKeysSize) {
            val preKeySize = inputStream.readInt()
            val preKeyBytes = inputStream.readNBytes(preKeySize)
            add(PreKeyRecord(preKeyBytes))
        }
    }
    val signedPreKeySize = inputStream.readInt()
    val signedPreKeyBytes = inputStream.readNBytes(signedPreKeySize)
    val signedPreKeyRecord = SignedPreKeyRecord(signedPreKeyBytes)
    return DesktopSignalProtocol(identityKeyPair, registrationId, preKeys, signedPreKeyRecord)
}

fun writeSignalProtocol(signalProtocol: SignalProtocol): ByteArray  {
    val byteStream = ByteArrayOutputStream()
    val dataStream = DataOutputStream(byteStream)
    val identityKeyPairBytes = signalProtocol.identityKeyPair.serialize()
    val identityKeyPairSize = identityKeyPairBytes.size
    dataStream.writeInt(identityKeyPairSize)
    dataStream.write(identityKeyPairBytes)
    dataStream.writeInt(signalProtocol.registrationId)
    val preKeys = signalProtocol.preKeys
    dataStream.writeInt(preKeys.size)
    preKeys.forEach {
        val preKeyBytes = it.serialize()
        val preKeySize = preKeyBytes.size
        dataStream.writeInt(preKeySize)
        dataStream.write(preKeyBytes)
    }
    val signedPreKeyBytes = signalProtocol.signedPreKey.serialize()
    val signedPreKeySize = signedPreKeyBytes.size
    dataStream.writeInt(signedPreKeySize)
    dataStream.write(signedPreKeyBytes)

    return byteStream.toByteArray()
}
