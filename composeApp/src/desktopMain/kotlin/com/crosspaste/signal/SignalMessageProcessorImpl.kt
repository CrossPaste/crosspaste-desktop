package com.crosspaste.signal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.SignalMessage

class SignalMessageProcessorImpl(
    appInstanceId: String,
    signalProtocolStore: SignalProtocolStoreInterface,
) : SignalMessageProcessor {

    val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)

    private val sessionCipher =
        SessionCipher(
            signalProtocolStore as DesktopSignalProtocolStore,
            signalProtocolAddress,
        )

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

    override fun getSignalAddress(): SignalAddress {
        return SignalAddress(signalProtocolAddress.name, signalProtocolAddress.deviceId)
    }

    override suspend fun decryptPreKeySignalMessage(preKeySignalMessageInterface: PreKeySignalMessageInterface): ByteArray {
        preKeySignalMessageInterface as DesktopPreKeySignalMessage
        lock.withLock {
            return sessionCipher.decrypt(preKeySignalMessageInterface.preKeySignalMessage)
        }
    }
}
