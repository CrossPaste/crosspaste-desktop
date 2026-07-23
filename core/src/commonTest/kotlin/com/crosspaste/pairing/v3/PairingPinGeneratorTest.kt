package com.crosspaste.pairing.v3

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingPinGeneratorTest {

    @Test
    fun testFormatPinPreservesLeadingZeros() {
        assertContentEquals("000000".toCharArray(), PairingPinGenerator.formatPin(0))
        assertContentEquals("000042".toCharArray(), PairingPinGenerator.formatPin(42))
        assertContentEquals("999999".toCharArray(), PairingPinGenerator.formatPin(999_999))
        assertContentEquals("100000".toCharArray(), PairingPinGenerator.formatPin(100_000))
    }

    @Test
    fun testUniformDecimal6AcceptsBelowRejectionBound() {
        // 4_293_999_999 = 0xFFF1_3D7F is the largest accepted u32; maps to 999999
        val accepted = "fff13d7f".hexToByteArray() + ByteArray(28)
        assertEquals(999_999, PairingPinGenerator.uniformDecimal6(accepted))

        // 0x00000000 maps to 000000
        assertEquals(0, PairingPinGenerator.uniformDecimal6(ByteArray(32)))
    }

    @Test
    fun testUniformDecimal6RejectsAboveBoundAndUsesNextWindow() {
        // First window 4_294_000_000 = 0xFFF1_3D80 is rejected, second window 0x00000007 accepted
        val material = "fff13d80".hexToByteArray() + "00000007".hexToByteArray() + ByteArray(24)
        assertEquals(7, PairingPinGenerator.uniformDecimal6(material))
    }

    @Test
    fun testUniformDecimal6AllWindowsRejected() {
        // 0xFFFFFFFF in every window: caller must re-derive material with the next counter
        val material = ByteArray(32) { 0xFF.toByte() }
        assertNull(PairingPinGenerator.uniformDecimal6(material))
    }

    @Test
    fun testDerivePinIsDeterministicAndSixDigits() =
        runTest {
            val secret = ByteArray(PairingV3.PIN_SECRET_SIZE) { it.toByte() }
            val context =
                PairingTranscriptCodec.encodePinContext(
                    sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 1 },
                    tokenGeneration = 1L,
                    acceptorAppInstanceId = "acceptor-app",
                    initiatorAppInstanceId = "initiator-app",
                    acceptorSignPublicKey = ByteArray(8) { 2 },
                    acceptorCryptPublicKey = ByteArray(8) { 3 },
                    initiatorSignPublicKey = ByteArray(8) { 4 },
                    initiatorCryptPublicKey = ByteArray(8) { 5 },
                )

            val pin1 = PairingPinGenerator.derivePin(secret, context)
            val pin2 = PairingPinGenerator.derivePin(secret, context)

            assertEquals(PairingV3.PIN_LENGTH, pin1.size)
            assertContentEquals(pin1, pin2)
            pin1.forEach { char -> assertEquals(true, char in '0'..'9') }
        }

    @Test
    fun testDifferentContextProducesDifferentPinDistribution() =
        runTest {
            val secret = ByteArray(PairingV3.PIN_SECRET_SIZE) { it.toByte() }
            // With one shared secret, distinct session contexts must not systematically
            // collide. A handful of contexts colliding to one PIN would indicate a
            // derivation bug rather than chance (each pair collides with p = 1e-6).
            val pins =
                (0..9).map { index ->
                    val context =
                        PairingTranscriptCodec.encodePinContext(
                            sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { index.toByte() },
                            tokenGeneration = 1L,
                            acceptorAppInstanceId = "acceptor-app",
                            initiatorAppInstanceId = "initiator-app",
                            acceptorSignPublicKey = ByteArray(8) { 2 },
                            acceptorCryptPublicKey = ByteArray(8) { 3 },
                            initiatorSignPublicKey = ByteArray(8) { 4 },
                            initiatorCryptPublicKey = ByteArray(8) { 5 },
                        )
                    PairingPinGenerator.derivePin(secret, context).concatToString()
                }
            assertEquals(true, pins.toSet().size > 1)
        }
}
