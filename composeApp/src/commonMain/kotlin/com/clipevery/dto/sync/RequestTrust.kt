package com.clipevery.dto.sync

import org.signal.libsignal.protocol.IdentityKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

data class RequestTrust(val identityKey: IdentityKey, val token: Int)

fun decodeRequestTrust(encoded: ByteArray): RequestTrust {
    val byteStream = ByteArrayInputStream(encoded)
    val dataStream = DataInputStream(byteStream)
    val token = dataStream.readInt()
    val serialize = dataStream.readNBytes(encoded.size - 4)
    val identityKey = IdentityKey(serialize)
    return RequestTrust(identityKey, token)
}

fun encodeRequestTrust(requestTrust: RequestTrust): ByteArray {
    val byteStream = ByteArrayOutputStream()
    val dataStream = DataOutputStream(byteStream)
    dataStream.writeInt(requestTrust.token)
    val identityKeyBytes = requestTrust.identityKey.serialize()
    dataStream.write(identityKeyBytes)
    return byteStream.toByteArray()
}