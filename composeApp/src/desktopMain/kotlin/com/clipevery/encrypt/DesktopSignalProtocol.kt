package com.clipevery.encrypt

import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import org.signal.libsignal.protocol.util.Medium
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.LinkedList


fun generatePreKeys(start: Int, count: Int): List<PreKeyRecord> {
    var newStart = start
    val results: MutableList<PreKeyRecord> = LinkedList()
    newStart--
    for (i in 0 until count) {
        results.add(PreKeyRecord((newStart + i) % (Medium.MAX_VALUE - 1) + 1, Curve.generateKeyPair()))
    }
    return results
}

@Throws(InvalidKeyException::class)
fun generateSignedPreKey(
    identityKeyPair: IdentityKeyPair,
    signedPreKeyId: Int
): SignedPreKeyRecord {
    val keyPair: ECKeyPair = Curve.generateKeyPair()
    val signature =
        Curve.calculateSignature(identityKeyPair.privateKey, keyPair.getPublicKey().serialize())
    return SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature)
}

class DesktopSignalProtocol(override val identityKeyPair: IdentityKeyPair,
                            override val registrationId: Int,
                            override val preKeys: List<PreKeyRecord>,
                            override val signedPreKey: SignedPreKeyRecord
    ): SignalProtocol {

    constructor(): this(
        IdentityKeyPair.generate(),
        KeyHelper.generateRegistrationId(false),
        generatePreKeys(0, 5))

    constructor(identityKeyPair:  IdentityKeyPair, registrationId: Int, preKeys: List<PreKeyRecord>):
            this(identityKeyPair,
                registrationId,
                preKeys,
                generateSignedPreKey(identityKeyPair, 5))
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
