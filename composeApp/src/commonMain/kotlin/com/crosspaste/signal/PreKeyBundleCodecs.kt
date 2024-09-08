package com.crosspaste.signal

interface PreKeyBundleCodecs {

    fun decodePreKeyBundle(encoded: ByteArray): PreKeyBundleInterface

    fun encodePreKeyBundle(preKeyBundleInterface: PreKeyBundleInterface): ByteArray
}
