package com.crosspaste.signal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.SignalProtocolStore

class SignalMessageProcessorImpl(
    appInstanceId: String,
    signalProtocolStore: SignalProtocolStore,
) : SignalMessageProcessor {

    val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)

    private val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)

    private val lock = Mutex()

    override suspend fun encrypt(data: ByteArray): ByteArray {
        return lock.withLock { sessionCipher.encrypt(data) }.serialize()
    }

    override suspend fun decryptSignalMessage(message: ByteArray): ByteArray {
        val signalMessage = SignalMessage(message)
        lock.withLock {
            return sessionCipher.decrypt(signalMessage)
        }
    }

    suspend fun decryptPreKeySignalMessage(preKeySignalMessage: PreKeySignalMessage): ByteArray {
        lock.withLock {
            return sessionCipher.decrypt(preKeySignalMessage)
        }
    }
}
