package com.crosspaste.platform.windows

import com.sun.jna.platform.win32.Crypt32Util.cryptProtectData
import com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData

object WindowDapiHelper {

    fun encryptData(data: ByteArray): ByteArray? = cryptProtectData(data)

    fun decryptData(encryptedData: ByteArray): ByteArray? = cryptUnprotectData(encryptedData)
}
