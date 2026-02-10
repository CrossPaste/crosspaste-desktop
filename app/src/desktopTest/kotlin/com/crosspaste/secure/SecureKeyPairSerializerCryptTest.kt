package com.crosspaste.secure

import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SecureKeyPairSerializerCryptTest {
    private val serializer = SecureKeyPairSerializer()

    @Test
    fun `encodeCryptPublicKey and decodeCryptPublicKey roundtrip`() {
        val keyPair = generateSecureKeyPair()
        val encoded = serializer.encodeCryptPublicKey(keyPair.cryptKeyPair.publicKey)
        assertNotNull(encoded)

        val decoded = serializer.decodeCryptPublicKey(encoded)
        val reEncoded = serializer.encodeCryptPublicKey(decoded)
        assertContentEquals(encoded, reEncoded)
    }

    @Test
    fun `encodeCryptPrivateKey and decodeCryptPrivateKey roundtrip`() {
        val keyPair = generateSecureKeyPair()
        val encoded = serializer.encodeCryptPrivateKey(keyPair.cryptKeyPair.privateKey)
        assertNotNull(encoded)

        val decoded = serializer.decodeCryptPrivateKey(encoded)
        val reEncoded = serializer.encodeCryptPrivateKey(decoded)
        assertContentEquals(encoded, reEncoded)
    }

    @Test
    fun `encodeCryptKeyPair and decodeCryptKeyPair roundtrip`() {
        val keyPair = generateSecureKeyPair()
        val encoded = serializer.encodeCryptKeyPair(keyPair.cryptKeyPair)
        assertNotNull(encoded)

        val decoded = serializer.decodeCryptKeyPair(encoded)
        val originalPubEncoded = serializer.encodeCryptPublicKey(keyPair.cryptKeyPair.publicKey)
        val decodedPubEncoded = serializer.encodeCryptPublicKey(decoded.publicKey)
        assertContentEquals(originalPubEncoded, decodedPubEncoded)

        val originalPrivEncoded = serializer.encodeCryptPrivateKey(keyPair.cryptKeyPair.privateKey)
        val decodedPrivEncoded = serializer.encodeCryptPrivateKey(decoded.privateKey)
        assertContentEquals(originalPrivEncoded, decodedPrivEncoded)
    }

    @Test
    fun `encodeSecureKeyPair and decodeSecureKeyPair roundtrip`() {
        val keyPair = generateSecureKeyPair()
        val encoded = serializer.encodeSecureKeyPair(keyPair)
        assertNotNull(encoded)

        val decoded = serializer.decodeSecureKeyPair(encoded)

        // Verify sign key pair
        val origSignPub = serializer.encodeSignPublicKey(keyPair.signKeyPair.publicKey)
        val decodedSignPub = serializer.encodeSignPublicKey(decoded.signKeyPair.publicKey)
        assertContentEquals(origSignPub, decodedSignPub)

        // Verify crypt key pair
        val origCryptPub = serializer.encodeCryptPublicKey(keyPair.cryptKeyPair.publicKey)
        val decodedCryptPub = serializer.encodeCryptPublicKey(decoded.cryptKeyPair.publicKey)
        assertContentEquals(origCryptPub, decodedCryptPub)
    }

    @Test
    fun `encodeSecureKeyPair multiple rounds are consistent`() {
        val keyPair = generateSecureKeyPair()
        val first = serializer.encodeSecureKeyPair(keyPair)
        val decoded = serializer.decodeSecureKeyPair(first)
        val second = serializer.encodeSecureKeyPair(decoded)
        assertContentEquals(first, second)
    }

    @Test
    fun `decodeCryptKeyPair with truncated data throws`() {
        assertFailsWith<IllegalArgumentException> {
            serializer.decodeCryptKeyPair(ByteArray(2))
        }
    }

    @Test
    fun `decodeSecureKeyPair with truncated data throws`() {
        assertFailsWith<IllegalArgumentException> {
            serializer.decodeSecureKeyPair(ByteArray(3))
        }
    }

    @Test
    fun `decodeSignKeyPair with truncated data throws`() {
        assertFailsWith<IllegalArgumentException> {
            serializer.decodeSignKeyPair(ByteArray(1))
        }
    }

    @Test
    fun `decodeSecureKeyPair with invalid size field throws`() {
        // Create bytes with a size value larger than actual content
        val bytes = ByteArray(8)
        // Set size = 100 but only have 4 bytes of content
        bytes[3] = 100.toByte()
        assertFailsWith<IllegalArgumentException> {
            serializer.decodeSecureKeyPair(bytes)
        }
    }

    @Test
    fun `different key pairs produce different encodings`() {
        val kp1 = generateSecureKeyPair()
        val kp2 = generateSecureKeyPair()
        val enc1 = serializer.encodeSecureKeyPair(kp1)
        val enc2 = serializer.encodeSecureKeyPair(kp2)
        assertNotNull(enc1)
        assertNotNull(enc2)
        // Different key pairs should produce different encodings
        assert(!enc1.contentEquals(enc2)) { "Different key pairs should have different encodings" }
    }
}
