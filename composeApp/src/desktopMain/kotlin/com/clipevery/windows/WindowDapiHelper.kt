package com.clipevery.windows

import com.sun.jna.platform.win32.Crypt32Util.cryptProtectData
import com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData

object WindowDapiHelper {

    fun encryptString(data: ByteArray): ByteArray? {
        return cryptProtectData(data)
    }

    fun decryptString(encryptedData: ByteArray): ByteArray? {
        return cryptUnprotectData(encryptedData)
    }
}