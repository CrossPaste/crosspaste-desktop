package com.crosspaste.signal

interface PreKeySignalMessageFactory {
    fun createPreKeySignalMessage(bytes: ByteArray): PreKeySignalMessageInterface
}

interface PreKeySignalMessageInterface {
    fun getSignedPreKeyId(): Int
}
