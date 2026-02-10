package com.crosspaste.utils

import com.crosspaste.dto.secure.PairingRequest
import com.crosspaste.dto.secure.PairingResponse
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import com.crosspaste.utils.CryptographyUtils.signData
import com.crosspaste.utils.CryptographyUtils.signPairingRequest
import com.crosspaste.utils.CryptographyUtils.signPairingResponse
import com.crosspaste.utils.CryptographyUtils.verifyData
import com.crosspaste.utils.CryptographyUtils.verifyPairingRequest
import com.crosspaste.utils.CryptographyUtils.verifyPairingResponse
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptographyUtilsTest {

    private val serializer = SecureKeyPairSerializer()

    @Test
    fun `generateSecureKeyPair produces valid key pair`() {
        val keyPair = generateSecureKeyPair()
        assertNotNull(keyPair.signKeyPair)
        assertNotNull(keyPair.cryptKeyPair)
        assertNotNull(keyPair.signKeyPair.publicKey)
        assertNotNull(keyPair.signKeyPair.privateKey)
        assertNotNull(keyPair.cryptKeyPair.publicKey)
        assertNotNull(keyPair.cryptKeyPair.privateKey)
    }

    @Test
    fun `generateSecureKeyPair produces unique key pairs`() {
        val kp1 = generateSecureKeyPair()
        val kp2 = generateSecureKeyPair()
        val bytes1 = serializer.encodeSignPublicKey(kp1.signKeyPair.publicKey)
        val bytes2 = serializer.encodeSignPublicKey(kp2.signKeyPair.publicKey)
        assertFalse(bytes1.contentEquals(bytes2), "Each generated key pair should be unique")
    }

    @Test
    fun `signData and verifyData roundtrip`() {
        val keyPair = generateSecureKeyPair()
        val data = "test data for signing".encodeToByteArray()
        val signature = signData(keyPair.signKeyPair.privateKey) { data }
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())

        val verified = verifyData(keyPair.signKeyPair.publicKey, signature) { data }
        assertTrue(verified)
    }

    @Test
    fun `verifyData fails with wrong public key`() {
        val keyPair1 = generateSecureKeyPair()
        val keyPair2 = generateSecureKeyPair()
        val data = "test data".encodeToByteArray()
        val signature = signData(keyPair1.signKeyPair.privateKey) { data }

        val verified = verifyData(keyPair2.signKeyPair.publicKey, signature) { data }
        assertFalse(verified)
    }

    @Test
    fun `verifyData fails with tampered data`() {
        val keyPair = generateSecureKeyPair()
        val data = "original data".encodeToByteArray()
        val signature = signData(keyPair.signKeyPair.privateKey) { data }

        val tampered = "tampered data".encodeToByteArray()
        val verified = verifyData(keyPair.signKeyPair.publicKey, signature) { tampered }
        assertFalse(verified)
    }

    @Test
    fun `signPairingRequest and verifyPairingRequest roundtrip`() {
        val keyPair = generateSecureKeyPair()
        val signPublicKeyBytes = serializer.encodeSignPublicKey(keyPair.signKeyPair.publicKey)
        val cryptPublicKeyBytes = serializer.encodeCryptPublicKey(keyPair.cryptKeyPair.publicKey)

        val request =
            PairingRequest(
                signPublicKey = signPublicKeyBytes,
                cryptPublicKey = cryptPublicKeyBytes,
                token = 123456,
                timestamp = System.currentTimeMillis(),
            )

        val signature = signPairingRequest(keyPair.signKeyPair.privateKey, request)
        assertNotNull(signature)

        val verified = verifyPairingRequest(keyPair.signKeyPair.publicKey, request, signature)
        assertTrue(verified)
    }

    @Test
    fun `verifyPairingRequest fails with different token`() {
        val keyPair = generateSecureKeyPair()
        val signPublicKeyBytes = serializer.encodeSignPublicKey(keyPair.signKeyPair.publicKey)
        val cryptPublicKeyBytes = serializer.encodeCryptPublicKey(keyPair.cryptKeyPair.publicKey)

        val request =
            PairingRequest(
                signPublicKey = signPublicKeyBytes,
                cryptPublicKey = cryptPublicKeyBytes,
                token = 123456,
                timestamp = System.currentTimeMillis(),
            )
        val signature = signPairingRequest(keyPair.signKeyPair.privateKey, request)

        val tamperedRequest = request.copy(token = 654321)
        val verified = verifyPairingRequest(keyPair.signKeyPair.publicKey, tamperedRequest, signature)
        assertFalse(verified)
    }

    @Test
    fun `signPairingResponse and verifyPairingResponse roundtrip`() {
        val keyPair = generateSecureKeyPair()
        val signPublicKeyBytes = serializer.encodeSignPublicKey(keyPair.signKeyPair.publicKey)
        val cryptPublicKeyBytes = serializer.encodeCryptPublicKey(keyPair.cryptKeyPair.publicKey)

        val response =
            PairingResponse(
                signPublicKey = signPublicKeyBytes,
                cryptPublicKey = cryptPublicKeyBytes,
                timestamp = System.currentTimeMillis(),
            )

        val signature = signPairingResponse(keyPair.signKeyPair.privateKey, response)
        assertNotNull(signature)

        val verified = verifyPairingResponse(keyPair.signKeyPair.publicKey, response, signature)
        assertTrue(verified)
    }

    @Test
    fun `verifyPairingResponse fails with wrong key`() {
        val keyPair1 = generateSecureKeyPair()
        val keyPair2 = generateSecureKeyPair()
        val signPublicKeyBytes = serializer.encodeSignPublicKey(keyPair1.signKeyPair.publicKey)
        val cryptPublicKeyBytes = serializer.encodeCryptPublicKey(keyPair1.cryptKeyPair.publicKey)

        val response =
            PairingResponse(
                signPublicKey = signPublicKeyBytes,
                cryptPublicKey = cryptPublicKeyBytes,
                timestamp = System.currentTimeMillis(),
            )

        val signature = signPairingResponse(keyPair1.signKeyPair.privateKey, response)
        val verified = verifyPairingResponse(keyPair2.signKeyPair.publicKey, response, signature)
        assertFalse(verified)
    }

    @Test
    fun `signData with empty data`() {
        val keyPair = generateSecureKeyPair()
        val signature = signData(keyPair.signKeyPair.privateKey) { ByteArray(0) }
        assertNotNull(signature)
        assertTrue(
            verifyData(keyPair.signKeyPair.publicKey, signature) { ByteArray(0) },
        )
    }

    @Test
    fun `signData with large data`() {
        val keyPair = generateSecureKeyPair()
        val largeData = ByteArray(10000) { it.toByte() }
        val signature = signData(keyPair.signKeyPair.privateKey) { largeData }
        assertTrue(
            verifyData(keyPair.signKeyPair.publicKey, signature) { largeData },
        )
    }
}
