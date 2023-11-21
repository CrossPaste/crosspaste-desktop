package com.clipevery.encrypt

import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.util.KeyHelper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
    val inputStream = ByteArrayInputStream(data)
    val identityKeyPairSize = inputStream.read()
    val identityKeyPairBytes = inputStream.readNBytes(identityKeyPairSize)
    val identityKeyPair = IdentityKeyPair(identityKeyPairBytes)
    val registrationId = inputStream.read()
    val preKeysSize = inputStream.read()
    val preKeys = buildList {
        for (i in 0 until preKeysSize) {
            val preKeySize = inputStream.read()
            val preKeyBytes = inputStream.readNBytes(preKeySize)
            add(PreKeyRecord(preKeyBytes))
        }
    }
    val signedPreKeySize = inputStream.read()
    val signedPreKeyBytes = inputStream.readNBytes(signedPreKeySize)
    val signedPreKeyRecord = SignedPreKeyRecord(signedPreKeyBytes)
    return DesktopSignalProtocol(identityKeyPair, registrationId, preKeys, signedPreKeyRecord)
}

fun writeSignalProtocol(signalProtocol: SignalProtocol): ByteArray  {
    val byteStream = ByteArrayOutputStream()
    val identityKeyPairBytes = signalProtocol.identityKeyPair.serialize()
    val identityKeyPairSize = identityKeyPairBytes.size
    byteStream.write(identityKeyPairSize)
    byteStream.write(identityKeyPairBytes)
    byteStream.write(signalProtocol.registrationId)
    val preKeys = signalProtocol.preKeys
    byteStream.write(preKeys.size)
    preKeys.forEach {
        val preKeyBytes = it.serialize()
        val preKeySize = preKeyBytes.size
        byteStream.write(preKeySize)
        byteStream.write(preKeyBytes)
    }
    val signedPreKeyBytes = signalProtocol.signedPreKey.serialize()
    val signedPreKeySize = signedPreKeyBytes.size
    byteStream.write(signedPreKeySize)
    byteStream.write(signedPreKeyBytes)

    return byteStream.toByteArray()
}
