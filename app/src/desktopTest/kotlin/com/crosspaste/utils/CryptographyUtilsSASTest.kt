package com.crosspaste.utils

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CryptographyUtilsSASTest {

    @Test
    fun `computeSAS returns 6-digit range`() =
        runBlocking {
            val keyA = byteArrayOf(1, 2, 3, 4, 5)
            val keyB = byteArrayOf(6, 7, 8, 9, 10)
            val sas = CryptographyUtils.computeSAS(keyA, keyB)
            assertTrue(sas in 0 until 1_000_000, "SAS must be in [0, 999999], got $sas")
        }

    @Test
    fun `computeSAS is deterministic`() =
        runBlocking {
            val keyA = byteArrayOf(10, 20, 30)
            val keyB = byteArrayOf(40, 50, 60)
            val sas1 = CryptographyUtils.computeSAS(keyA, keyB)
            val sas2 = CryptographyUtils.computeSAS(keyA, keyB)
            assertEquals(sas1, sas2)
        }

    @Test
    fun `computeSAS is order-independent`() =
        runBlocking {
            val keyA = byteArrayOf(10, 20, 30)
            val keyB = byteArrayOf(40, 50, 60)
            val sasAB = CryptographyUtils.computeSAS(keyA, keyB)
            val sasBA = CryptographyUtils.computeSAS(keyB, keyA)
            assertEquals(sasAB, sasBA, "SAS must be the same regardless of key order")
        }

    @Test
    fun `computeSAS differs for different keys`() =
        runBlocking {
            val keyA = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            val keyB = byteArrayOf(9, 10, 11, 12, 13, 14, 15, 16)
            val keyC = byteArrayOf(17, 18, 19, 20, 21, 22, 23, 24)
            val sasAB = CryptographyUtils.computeSAS(keyA, keyB)
            val sasAC = CryptographyUtils.computeSAS(keyA, keyC)
            // Extremely unlikely to collide with different keys
            assertTrue(sasAB != sasAC, "Different key pairs should produce different SAS values")
        }

    @Test
    fun `computeSAS with real ECDH keys`() =
        runBlocking {
            val keyPairA = CryptographyUtils.generateSecureKeyPair()
            val keyPairB = CryptographyUtils.generateSecureKeyPair()
            val serializer = com.crosspaste.secure.SecureKeyPairSerializer()

            val pubA = serializer.encodeCryptPublicKey(keyPairA.cryptKeyPair.publicKey)
            val pubB = serializer.encodeCryptPublicKey(keyPairB.cryptKeyPair.publicKey)

            val sas = CryptographyUtils.computeSAS(pubA, pubB)
            assertTrue(sas in 0 until 1_000_000)

            // Order independence with real keys
            val sasReverse = CryptographyUtils.computeSAS(pubB, pubA)
            assertEquals(sas, sasReverse)
        }
}
