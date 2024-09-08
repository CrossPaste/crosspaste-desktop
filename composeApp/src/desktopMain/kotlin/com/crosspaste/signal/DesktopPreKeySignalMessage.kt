package com.crosspaste.signal

import org.signal.libsignal.protocol.message.PreKeySignalMessage

class DesktopPreKeySignalMessageFactory : PreKeySignalMessageFactory {
    override fun createPreKeySignalMessage(bytes: ByteArray): PreKeySignalMessageInterface {
        return DesktopPreKeySignalMessage(bytes)
    }
}

class DesktopPreKeySignalMessage(bytes: ByteArray) : PreKeySignalMessageInterface {

    val preKeySignalMessage = PreKeySignalMessage(bytes)

    override fun getSignedPreKeyId(): Int {
        return preKeySignalMessage.signedPreKeyId
    }
}
