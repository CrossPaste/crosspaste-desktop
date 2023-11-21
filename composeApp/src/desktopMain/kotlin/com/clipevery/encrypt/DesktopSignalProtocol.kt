package com.clipevery.encrypt

import com.clipevery.utils.readJson
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.util.KeyHelper

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


data class StringEncodeSignalProtocol(val identityKeyPairStr: String,
                                      val registrationIdStr: Int,
                                      val preKeysStr: List<String>,
                                      val signedPreKeyStr: String)


fun readSignalProtocol(data: String): SignalProtocol {
    val stringEncodeSignalProtocol = readJson<StringEncodeSignalProtocol>(data)

    val identityKeyPair = IdentityKeyPair(asciiStringToBytes(stringEncodeSignalProtocol.identityKeyPairStr))

    val registrationId = stringEncodeSignalProtocol.registrationIdStr

    val preKeys = stringEncodeSignalProtocol.preKeysStr.map { PreKeyRecord(asciiStringToBytes(it)) }

    val signedPreKey = SignedPreKeyRecord(asciiStringToBytes(stringEncodeSignalProtocol.signedPreKeyStr))

    return DesktopSignalProtocol(identityKeyPair, registrationId, preKeys, signedPreKey)
}

fun writeSignalProtocol(signalProtocol: SignalProtocol): StringEncodeSignalProtocol  {
    val identityKeyPairStr = bytesToAsciiString(signalProtocol.identityKeyPair.serialize())

    val registrationIdStr = signalProtocol.registrationId

    val preKeysStr = signalProtocol.preKeys.map { bytesToAsciiString(it.serialize()) }

    val signedPreKeyStr = bytesToAsciiString(signalProtocol.signedPreKey.serialize())

    return StringEncodeSignalProtocol(identityKeyPairStr, registrationIdStr, preKeysStr, signedPreKeyStr)
}

fun bytesToAsciiString(bytes: ByteArray): String {
    return bytes.joinToString(separator = "") { it.toInt().toChar().toString() }
}

fun asciiStringToBytes(str: String): ByteArray {
    return str.map { it.code.toByte() }.toByteArray()
}
