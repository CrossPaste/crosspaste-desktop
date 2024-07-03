package com.crosspaste.signal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.SignalProtocolStore

class SignalMessageProcessorImpl(
    appInstanceId: String,
    signalProtocolStore: SignalProtocolStore,
) : SignalMessageProcessor {

    override val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)

    private val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)

    private val lock = Mutex()

    override suspend fun encrypt(data: ByteArray): CiphertextMessage {
        lock.withLock {
            return sessionCipher.encrypt(data)
        }
    }

    override suspend fun decrypt(signalMessage: SignalMessage): ByteArray {
        lock.withLock {
            return sessionCipher.decrypt(signalMessage)
        }
    }

    override suspend fun decrypt(preKeySignalMessage: PreKeySignalMessage): ByteArray {
        lock.withLock {
            return sessionCipher.decrypt(preKeySignalMessage)
        }
    }
}
