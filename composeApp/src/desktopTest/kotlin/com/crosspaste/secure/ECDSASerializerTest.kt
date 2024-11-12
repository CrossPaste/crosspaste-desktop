package com.crosspaste.secure

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

class ECDSASerializerTest {
    private val serializer = ECDSASerializer()

    private fun generateIdentityKeyPair(): ECDSA.KeyPair {
        val provider = CryptographyProvider.Default
        val ecdsa = provider.get(ECDSA)
        val keyPairGenerator = ecdsa.keyPairGenerator(EC.Curve.P256)
        return keyPairGenerator.generateKeyBlocking()
    }

    @Test
    fun testPublicKeyEncodingDecoding() {
        // Generate a test key pair
        val keyPair = generateIdentityKeyPair()

        // Encode public key
        val encodedPublicKey = serializer.encodePublicKey(keyPair.publicKey)
        assertNotNull(encodedPublicKey, "Encoded public key should not be null")

        // Decode public key
        val decodedPublicKey = serializer.decodePublicKey(encodedPublicKey)

        // Re-encode the decoded key and compare with original encoding
        val reEncodedPublicKey = serializer.encodePublicKey(decodedPublicKey)
        assertContentEquals(
            encodedPublicKey,
            reEncodedPublicKey,
            "Re-encoded public key should match original encoded key"
        )
    }

    @Test
    fun testPrivateKeyEncodingDecoding() {
        // Generate a test key pair
        val keyPair = generateIdentityKeyPair()

        // Encode private key
        val encodedPrivateKey = serializer.encodePrivateKey(keyPair.privateKey)
        assertNotNull(encodedPrivateKey, "Encoded private key should not be null")

        // Decode private key
        val decodedPrivateKey = serializer.decodePrivateKey(encodedPrivateKey)

        // Re-encode the decoded key and compare with original encoding
        val reEncodedPrivateKey = serializer.encodePrivateKey(decodedPrivateKey)
        assertContentEquals(
            encodedPrivateKey,
            reEncodedPrivateKey,
            "Re-encoded private key should match original encoded key"
        )
    }

    @Test
    fun testKeyPairEncodingDecoding() {
        // Generate a test key pair
        val originalKeyPair = generateIdentityKeyPair()

        // Encode the key pair
        val encodedKeyPair = serializer.encodeKeyPair(originalKeyPair)
        assertNotNull(encodedKeyPair, "Encoded key pair should not be null")

        // Decode the key pair
        val decodedKeyPair = serializer.decodeKeyPair(encodedKeyPair)

        // Compare original and decoded key pairs by comparing their encoded forms
        val originalPublicKeyEncoded = serializer.encodePublicKey(originalKeyPair.publicKey)
        val decodedPublicKeyEncoded = serializer.encodePublicKey(decodedKeyPair.publicKey)
        assertContentEquals(
            originalPublicKeyEncoded,
            decodedPublicKeyEncoded,
            "Decoded public key should match original"
        )

        val originalPrivateKeyEncoded = serializer.encodePrivateKey(originalKeyPair.privateKey)
        val decodedPrivateKeyEncoded = serializer.encodePrivateKey(decodedKeyPair.privateKey)
        assertContentEquals(
            originalPrivateKeyEncoded,
            decodedPrivateKeyEncoded,
            "Decoded private key should match original"
        )
    }

    @Test
    fun testKeyPairEncodingWithMultipleRounds() {
        // Generate a test key pair
        val originalKeyPair = generateIdentityKeyPair()

        // First round of encoding/decoding
        val firstEncodedKeyPair = serializer.encodeKeyPair(originalKeyPair)
        val firstDecodedKeyPair = serializer.decodeKeyPair(firstEncodedKeyPair)

        // Second round of encoding/decoding
        val secondEncodedKeyPair = serializer.encodeKeyPair(firstDecodedKeyPair)
        val secondDecodedKeyPair = serializer.decodeKeyPair(secondEncodedKeyPair)

        // Compare the results of both rounds
        assertContentEquals(
            firstEncodedKeyPair,
            secondEncodedKeyPair,
            "Multiple rounds of encoding should produce consistent results"
        )

        // Verify final decoded keys match original
        val originalPublicKeyEncoded = serializer.encodePublicKey(originalKeyPair.publicKey)
        val finalPublicKeyEncoded = serializer.encodePublicKey(secondDecodedKeyPair.publicKey)
        assertContentEquals(
            originalPublicKeyEncoded,
            finalPublicKeyEncoded,
            "Public key should remain consistent after multiple encoding rounds"
        )

        val originalPrivateKeyEncoded = serializer.encodePrivateKey(originalKeyPair.privateKey)
        val finalPrivateKeyEncoded = serializer.encodePrivateKey(secondDecodedKeyPair.privateKey)
        assertContentEquals(
            originalPrivateKeyEncoded,
            finalPrivateKeyEncoded,
            "Private key should remain consistent after multiple encoding rounds"
        )
    }
}