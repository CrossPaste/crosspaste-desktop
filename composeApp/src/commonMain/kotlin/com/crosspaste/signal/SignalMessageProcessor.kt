package com.crosspaste.signal

interface SignalMessageProcessor {

    suspend fun encrypt(data: ByteArray): ByteArray

    suspend fun decryptSignalMessage(message: ByteArray): ByteArray
}
