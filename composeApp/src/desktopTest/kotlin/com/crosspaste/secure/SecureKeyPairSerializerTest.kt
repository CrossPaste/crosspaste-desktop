package com.crosspaste.secure

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

class SecureKeyPairSerializerTest {
    private val serializer = SecureKeyPairSerializer()

    private fun generateSecureKeyPair(): SecureKeyPair {
        val provider = CryptographyProvider.Default
        val ecdsa = provider.get(ECDSA)
        val ecdh = provider.get(ECDH)
        val signKeyPairGenerator = ecdsa.keyPairGenerator(EC.Curve.P256)
        val signKeyPair = signKeyPairGenerator.generateKeyBlocking()

        val cryptKeyPairGenerator = ecdh.keyPairGenerator(EC.Curve.P256)
        val cryptKeyPair = cryptKeyPairGenerator.generateKeyBlocking()
        return SecureKeyPair(signKeyPair, cryptKeyPair)

    }

    @Test
    fun testPublicKeyEncodingDecoding() {
        // Generate a test key pair
        val secureKeyPair = generateSecureKeyPair()

        // Encode public key
        val encodedPublicKey = serializer.encodeSignPublicKey(secureKeyPair.signKeyPair.publicKey)
        assertNotNull(encodedPublicKey, "Encoded public key should not be null")

        // Decode public key
        val decodedPublicKey = serializer.decodeSignPublicKey(encodedPublicKey)

        // Re-encode the decoded key and compare with original encoding
        val reEncodedPublicKey = serializer.encodeSignPublicKey(decodedPublicKey)
        assertContentEquals(
            encodedPublicKey,
            reEncodedPublicKey,
            "Re-encoded public key should match original encoded key"
        )
    }

    @Test
    fun testPrivateKeyEncodingDecoding() {
        // Generate a test key pair
        val keyPair = generateSecureKeyPair()

        // Encode private key
        val encodedPrivateKey = serializer.encodeSignPrivateKey(keyPair.signKeyPair.privateKey)
        assertNotNull(encodedPrivateKey, "Encoded private key should not be null")

        // Decode private key
        val decodedPrivateKey = serializer.decodeSignPrivateKey(encodedPrivateKey)

        // Re-encode the decoded key and compare with original encoding
        val reEncodedPrivateKey = serializer.encodeSignPrivateKey(decodedPrivateKey)
        assertContentEquals(
            encodedPrivateKey,
            reEncodedPrivateKey,
            "Re-encoded private key should match original encoded key"
        )
    }

    @Test
    fun testKeyPairEncodingDecoding() {
        // Generate a test key pair
        val originalKeyPair = generateSecureKeyPair()

        // Encode the key pair
        val encodedKeyPair = serializer.encodeSignKeyPair(originalKeyPair.signKeyPair)
        assertNotNull(encodedKeyPair, "Encoded key pair should not be null")

        // Decode the key pair
        val decodedKeyPair = serializer.decodeSignKeyPair(encodedKeyPair)

        // Compare original and decoded key pairs by comparing their encoded forms
        val originalPublicKeyEncoded = serializer.encodeSignPublicKey(originalKeyPair.signKeyPair.publicKey)
        val decodedPublicKeyEncoded = serializer.encodeSignPublicKey(decodedKeyPair.publicKey)
        assertContentEquals(
            originalPublicKeyEncoded,
            decodedPublicKeyEncoded,
            "Decoded public key should match original"
        )

        val originalPrivateKeyEncoded = serializer.encodeSignPrivateKey(originalKeyPair.signKeyPair.privateKey)
        val decodedPrivateKeyEncoded = serializer.encodeSignPrivateKey(decodedKeyPair.privateKey)
        assertContentEquals(
            originalPrivateKeyEncoded,
            decodedPrivateKeyEncoded,
            "Decoded private key should match original"
        )
    }

    @Test
    fun testKeyPairEncodingWithMultipleRounds() {
        // Generate a test key pair
        val originalKeyPair = generateSecureKeyPair()

        // First round of encoding/decoding
        val firstEncodedKeyPair = serializer.encodeSignKeyPair(originalKeyPair.signKeyPair)
        val firstDecodedKeyPair = serializer.decodeSignKeyPair(firstEncodedKeyPair)

        // Second round of encoding/decoding
        val secondEncodedKeyPair = serializer.encodeSignKeyPair(firstDecodedKeyPair)
        val secondDecodedKeyPair = serializer.decodeSignKeyPair(secondEncodedKeyPair)

        // Compare the results of both rounds
        assertContentEquals(
            firstEncodedKeyPair,
            secondEncodedKeyPair,
            "Multiple rounds of encoding should produce consistent results"
        )

        // Verify final decoded keys match original
        val originalPublicKeyEncoded = serializer.encodeSignPublicKey(originalKeyPair.signKeyPair.publicKey)
        val finalPublicKeyEncoded = serializer.encodeSignPublicKey(secondDecodedKeyPair.publicKey)
        assertContentEquals(
            originalPublicKeyEncoded,
            finalPublicKeyEncoded,
            "Public key should remain consistent after multiple encoding rounds"
        )

        val originalPrivateKeyEncoded = serializer.encodeSignPrivateKey(originalKeyPair.signKeyPair.privateKey)
        val finalPrivateKeyEncoded = serializer.encodeSignPrivateKey(secondDecodedKeyPair.privateKey)
        assertContentEquals(
            originalPrivateKeyEncoded,
            finalPrivateKeyEncoded,
            "Private key should remain consistent after multiple encoding rounds"
        )
    }
}