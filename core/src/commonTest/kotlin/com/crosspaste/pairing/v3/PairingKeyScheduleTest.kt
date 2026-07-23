package com.crosspaste.pairing.v3

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingKeyScheduleTest {

    private val transcriptHash = ByteArray(32) { 0x42 }

    private val pakeSharedSecret = ByteArray(32) { 0x24 }

    @Test
    fun testDerivedKeysAreIndependentPerLabel() =
        runTest {
            val keys = PairingKeySchedule.deriveSessionKeys(transcriptHash, pakeSharedSecret)

            val allKeys =
                listOf(
                    keys.confirmInitiator.toHexString(),
                    keys.confirmAcceptor.toHexString(),
                    keys.handshakeAead.toHexString(),
                    keys.receipt.toHexString(),
                )

            assertEquals(4, allKeys.toSet().size)
            allKeys.forEach { key -> assertEquals(PairingV3.DERIVED_KEY_SIZE * 2, key.length) }
        }

    @Test
    fun testDerivationIsDeterministic() =
        runTest {
            val keys1 = PairingKeySchedule.deriveSessionKeys(transcriptHash, pakeSharedSecret)
            val keys2 = PairingKeySchedule.deriveSessionKeys(transcriptHash, pakeSharedSecret)

            assertContentEquals(keys1.confirmInitiator, keys2.confirmInitiator)
            assertContentEquals(keys1.confirmAcceptor, keys2.confirmAcceptor)
            assertContentEquals(keys1.handshakeAead, keys2.handshakeAead)
            assertContentEquals(keys1.receipt, keys2.receipt)
        }

    @Test
    fun testDifferentTranscriptOrSecretChangesAllKeys() =
        runTest {
            val base = PairingKeySchedule.deriveSessionKeys(transcriptHash, pakeSharedSecret)
            val otherTranscript = PairingKeySchedule.deriveSessionKeys(ByteArray(32) { 0x43 }, pakeSharedSecret)
            val otherSecret = PairingKeySchedule.deriveSessionKeys(transcriptHash, ByteArray(32) { 0x25 })

            assertFalse(base.confirmInitiator.contentEquals(otherTranscript.confirmInitiator))
            assertFalse(base.confirmInitiator.contentEquals(otherSecret.confirmInitiator))
            assertFalse(base.receipt.contentEquals(otherTranscript.receipt))
            assertFalse(base.receipt.contentEquals(otherSecret.receipt))
        }

    @Test
    fun testConfirmationMacsDifferByRoleAndContext() =
        runTest {
            val keys = PairingKeySchedule.deriveSessionKeys(transcriptHash, pakeSharedSecret)

            val macs =
                listOf(
                    PairingKeySchedule.initiatorConfirmation(keys, transcriptHash).toHexString(),
                    PairingKeySchedule.acceptorConfirmation(keys, transcriptHash).toHexString(),
                    PairingKeySchedule.commitMac(keys, transcriptHash).toHexString(),
                    PairingKeySchedule.receiptMac(keys, transcriptHash).toHexString(),
                )

            assertEquals(4, macs.toSet().size)
        }

    @Test
    fun testIdentitySignaturePayloadIsRoleSpecific() {
        val initiatorPayload = PairingKeySchedule.identitySignaturePayload(PakeRole.INITIATOR, transcriptHash)
        val acceptorPayload = PairingKeySchedule.identitySignaturePayload(PakeRole.ACCEPTOR, transcriptHash)

        assertFalse(initiatorPayload.contentEquals(acceptorPayload))
        assertContentEquals(transcriptHash, initiatorPayload.copyOfRange(0, 32))
    }

    @Test
    fun testConstantTimeEquals() {
        val a = ByteArray(32) { 0x01 }
        val b = ByteArray(32) { 0x01 }
        val c = ByteArray(32) { 0x01 }.also { array -> array[31] = 0x02 }
        val shorter = ByteArray(31) { 0x01 }

        assertTrue(PairingKeySchedule.constantTimeEquals(a, b))
        assertFalse(PairingKeySchedule.constantTimeEquals(a, c))
        assertFalse(PairingKeySchedule.constantTimeEquals(a, shorter))
    }

    @Test
    fun testClearZeroizesKeys() =
        runTest {
            val keys = PairingKeySchedule.deriveSessionKeys(transcriptHash, pakeSharedSecret)
            keys.clear()

            assertContentEquals(ByteArray(PairingV3.DERIVED_KEY_SIZE), keys.confirmInitiator)
            assertContentEquals(ByteArray(PairingV3.DERIVED_KEY_SIZE), keys.confirmAcceptor)
            assertContentEquals(ByteArray(PairingV3.DERIVED_KEY_SIZE), keys.handshakeAead)
            assertContentEquals(ByteArray(PairingV3.DERIVED_KEY_SIZE), keys.receipt)
        }
}
