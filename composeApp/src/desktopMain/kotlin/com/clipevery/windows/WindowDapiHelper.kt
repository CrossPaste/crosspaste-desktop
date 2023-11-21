package com.clipevery.windows

import com.sun.jna.platform.win32.Crypt32Util.cryptProtectData
import com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData

object WindowDapiHelper {

    fun encryptData(data: ByteArray): ByteArray? {
        return cryptProtectData(data)
    }

    fun decryptData(encryptedData: ByteArray): ByteArray? {
        return cryptUnprotectData(encryptedData)
    }
}