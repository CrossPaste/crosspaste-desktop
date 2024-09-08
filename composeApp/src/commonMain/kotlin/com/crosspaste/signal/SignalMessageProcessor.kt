package com.crosspaste.signal

interface SignalMessageProcessor {

    fun getSignalAddress(): SignalAddress

    suspend fun encrypt(data: ByteArray): ByteArray

    suspend fun decryptSignalMessage(message: ByteArray): ByteArray

    suspend fun decryptPreKeySignalMessage(preKeySignalMessageInterface: PreKeySignalMessageInterface): ByteArray
}
