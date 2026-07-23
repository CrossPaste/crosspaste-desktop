package com.crosspaste.pairing.v3

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PairingTranscriptCodecTest {

    private fun sampleTranscript(): PairingTranscript =
        PairingTranscript(
            protocolVersion = PairingV3.PROTOCOL_VERSION,
            selectedCiphersuite = PairingV3.CIPHERSUITE_SPAKE2_P256,
            sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x11 },
            tokenGeneration = 1L,
            initiatorAppInstanceId = "initiator-app",
            acceptorAppInstanceId = "acceptor-app",
            initiatorNonce = ByteArray(PairingV3.NONCE_SIZE) { 0x22 },
            acceptorNonce = ByteArray(PairingV3.NONCE_SIZE) { 0x33 },
            initiatorSignPublicKey = ByteArray(8) { 0x44 },
            initiatorCryptPublicKey = ByteArray(8) { 0x55 },
            acceptorSignPublicKey = ByteArray(8) { 0x66 },
            acceptorCryptPublicKey = ByteArray(8) { 0x77 },
            initiatorPakeShare = ByteArray(8) { 0x88.toByte() },
            acceptorPakeShare = ByteArray(8) { 0x99.toByte() },
            intentHash = ByteArray(32) { 0xAA.toByte() },
            offerHash = ByteArray(32) { 0xBB.toByte() },
            negotiatedCapabilities = listOf("cap-a", "cap-b"),
        )

    /**
     * Structural golden vector: the expected bytes are constructed independently of
     * [CanonicalWriter] to freeze the wire format (domain prefix, field ids, big-endian
     * length prefixes). Any codec change that alters bytes must fail here.
     */
    @Test
    fun testCanonicalEncodingGoldenVector() {
        val sessionId = byteArrayOf(0x01, 0x02)
        val encoded =
            CanonicalWriter("Test-Domain")
                .field(0, 3)
                .field(1, sessionId)
                .field(2, 7L)
                .field(3, "ab")
                .field(4, listOf("x", "yz"))
                .build()

        val expected =
            byteArrayOf(0x00, 0x00, 0x00, 0x0B) + "Test-Domain".encodeToByteArray() +
                // field 0: int 3
                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x03) +
                // field 1: bytes 0102
                byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x02, 0x01, 0x02) +
                // field 2: long 7
                byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07) +
                // field 3: string "ab"
                byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x02) + "ab".encodeToByteArray() +
                // field 4: list ["x", "yz"] = count 2, len 1 "x", len 2 "yz"
                byteArrayOf(0x04, 0x00, 0x00, 0x00, 0x0F) +
                byteArrayOf(0x00, 0x00, 0x00, 0x02) +
                byteArrayOf(0x00, 0x00, 0x00, 0x01) + "x".encodeToByteArray() +
                byteArrayOf(0x00, 0x00, 0x00, 0x02) + "yz".encodeToByteArray()

        assertContentEquals(expected, encoded)
    }

    @Test
    fun testTranscriptHashIsDeterministicAcrossRoles() =
        runTest {
            // Both roles construct the transcript independently from the same session data
            val initiatorView = sampleTranscript()
            val acceptorView = sampleTranscript()

            assertContentEquals(
                PairingTranscriptCodec.transcriptHash(initiatorView),
                PairingTranscriptCodec.transcriptHash(acceptorView),
            )
        }

    @Test
    fun testEveryTranscriptFieldChangesTheHash() =
        runTest {
            val base = sampleTranscript()
            val baseHash = PairingTranscriptCodec.transcriptHash(base).toHexString()

            val mutations =
                listOf(
                    base.copy(protocolVersion = 4),
                    base.copy(selectedCiphersuite = "other-suite"),
                    base.copy(sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x12 }),
                    base.copy(tokenGeneration = 2L),
                    base.copy(initiatorAppInstanceId = "initiator-app2"),
                    base.copy(acceptorAppInstanceId = "acceptor-app2"),
                    base.copy(initiatorNonce = ByteArray(PairingV3.NONCE_SIZE) { 0x23 }),
                    base.copy(acceptorNonce = ByteArray(PairingV3.NONCE_SIZE) { 0x34 }),
                    base.copy(initiatorSignPublicKey = ByteArray(8) { 0x45 }),
                    base.copy(initiatorCryptPublicKey = ByteArray(8) { 0x56 }),
                    base.copy(acceptorSignPublicKey = ByteArray(8) { 0x67 }),
                    base.copy(acceptorCryptPublicKey = ByteArray(8) { 0x78 }),
                    base.copy(initiatorPakeShare = ByteArray(8) { 0x89.toByte() }),
                    base.copy(acceptorPakeShare = ByteArray(8) { 0x9A.toByte() }),
                    base.copy(intentHash = ByteArray(32) { 0xAB.toByte() }),
                    base.copy(offerHash = ByteArray(32) { 0xBC.toByte() }),
                    base.copy(negotiatedCapabilities = listOf("cap-a")),
                )

            val hashes = mutations.map { PairingTranscriptCodec.transcriptHash(it).toHexString() }

            hashes.forEach { mutatedHash -> assertNotEquals(baseHash, mutatedHash) }
            // All mutations must also be distinct from each other
            assertEquals(mutations.size, hashes.toSet().size)
        }

    @Test
    fun testRoleSwapChangesTheHash() =
        runTest {
            val base = sampleTranscript()
            val swapped =
                base.copy(
                    initiatorAppInstanceId = base.acceptorAppInstanceId,
                    acceptorAppInstanceId = base.initiatorAppInstanceId,
                    initiatorNonce = base.acceptorNonce,
                    acceptorNonce = base.initiatorNonce,
                    initiatorSignPublicKey = base.acceptorSignPublicKey,
                    initiatorCryptPublicKey = base.acceptorCryptPublicKey,
                    acceptorSignPublicKey = base.initiatorSignPublicKey,
                    acceptorCryptPublicKey = base.initiatorCryptPublicKey,
                    initiatorPakeShare = base.acceptorPakeShare,
                    acceptorPakeShare = base.initiatorPakeShare,
                )

            assertNotEquals(
                PairingTranscriptCodec.transcriptHash(base).toHexString(),
                PairingTranscriptCodec.transcriptHash(swapped).toHexString(),
            )
        }

    @Test
    fun testFieldBoundariesAreUnambiguous() {
        // Length prefixes prevent concatenation ambiguity: ("ab", "c") != ("a", "bc")
        val encoded1 = CanonicalWriter("D").field(0, "ab").field(1, "c").build()
        val encoded2 = CanonicalWriter("D").field(0, "a").field(1, "bc").build()
        assertEquals(false, encoded1.contentEquals(encoded2))
    }
}
