package com.clipevery.signal

import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage

interface SignalMessageProcessor {

    val signalProtocolAddress: SignalProtocolAddress

    suspend fun encrypt(data: ByteArray): CiphertextMessage

    suspend fun decrypt(signalMessage: SignalMessage): ByteArray

    suspend fun decrypt(preKeySignalMessage: PreKeySignalMessage): ByteArray
}
