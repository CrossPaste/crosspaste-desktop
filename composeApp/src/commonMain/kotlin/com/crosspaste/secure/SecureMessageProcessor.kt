package com.crosspaste.secure

interface SecureMessageProcessor {

    fun encrypt(data: ByteArray): ByteArray

    fun decrypt(data: ByteArray): ByteArray
}