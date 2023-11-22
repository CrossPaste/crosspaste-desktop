package com.clipevery.windows

import com.clipevery.encrypt.DesktopSignalProtocol
import com.clipevery.encrypt.readSignalProtocol
import com.clipevery.encrypt.writeSignalProtocol
import com.clipevery.platform.currentPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowDapiHelperTest {

    @Test
    fun testDapi() {
        val currentPlatform = currentPlatform()
        if (currentPlatform.isWindows()) {
            val str = "test windows dapi"
            val encryptData = WindowDapiHelper.encryptData(str.encodeToByteArray())
            assertTrue { (encryptData?.size ?: 0) > 0 }
            val decryptData = WindowDapiHelper.decryptData(encryptData!!)
            assertTrue { (decryptData?.size ?: 0) > 0 }
            assertEquals(str, String(decryptData!!))
        }
    }

    @Test
    fun testSignalProtocolDapi() {
        val currentPlatform = currentPlatform()
        if (currentPlatform.isWindows()) {
            val signalProtocol = DesktopSignalProtocol()
            val data = writeSignalProtocol(signalProtocol)
            val encryptData = WindowDapiHelper.encryptData(data)
            assertTrue { (encryptData?.size ?: 0) > 0 }
            val decryptData = WindowDapiHelper.decryptData(encryptData!!)
            assertTrue { (decryptData?.size ?: 0) > 0 }

            assertTrue { data.contentEquals(decryptData!!) }

            val newSignalProtocol = readSignalProtocol(decryptData!!)
            assertEquals(signalProtocol.identityKeyPair.publicKey,
                newSignalProtocol.identityKeyPair.publicKey)
            assertTrue {
                signalProtocol.identityKeyPair.privateKey.serialize()
                    .contentEquals(newSignalProtocol.identityKeyPair.privateKey.serialize())
            }
            assertEquals(signalProtocol.registrationId, newSignalProtocol.registrationId)
            assertEquals(signalProtocol.preKeys.size, newSignalProtocol.preKeys.size)
            for (i in 0 until signalProtocol.preKeys.size) {
                assertEquals(signalProtocol.preKeys[i].id, newSignalProtocol.preKeys[i].id)
                assertEquals(signalProtocol.preKeys[i].keyPair.publicKey,
                    newSignalProtocol.preKeys[i].keyPair.publicKey)
                assertTrue {
                    signalProtocol.preKeys[i].keyPair.privateKey.serialize()
                        .contentEquals(newSignalProtocol.preKeys[i].keyPair.privateKey.serialize()) }
            }
            assertEquals(signalProtocol.signedPreKey.id,
                newSignalProtocol.signedPreKey.id)
            assertEquals(signalProtocol.signedPreKey.keyPair.publicKey,
                newSignalProtocol.signedPreKey.keyPair.publicKey)

            assertTrue {
                signalProtocol.signedPreKey.keyPair.privateKey.serialize()
                    .contentEquals(newSignalProtocol.signedPreKey.keyPair.privateKey.serialize()) }

            assertTrue { signalProtocol.signedPreKey.signature
                .contentEquals(newSignalProtocol.signedPreKey.signature) }
        }
    }
}