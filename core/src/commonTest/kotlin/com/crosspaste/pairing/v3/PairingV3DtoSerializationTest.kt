package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingCancelV3
import com.crosspaste.dto.pairing.v3.PairingCommitAckV3
import com.crosspaste.dto.pairing.v3.PairingCommitV3
import com.crosspaste.dto.pairing.v3.PairingErrorV3
import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingOfferV3
import com.crosspaste.dto.pairing.v3.PairingProofResponseV3
import com.crosspaste.dto.pairing.v3.PairingProofV3
import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertEquals

class PairingV3DtoSerializationTest {

    private val json = getJsonUtils().JSON

    private val intent =
        PairingIntentV3(
            protocolVersion = PairingV3.PROTOCOL_VERSION,
            requestId = ByteArray(PairingV3.REQUEST_ID_SIZE) { 0x01 },
            initiatorAppInstanceId = "initiator-app",
            targetAppInstanceId = "acceptor-app",
            initiatorDisplayName = "My Phone",
            initiatorSignPublicKey = ByteArray(8) { 0x02 },
            initiatorCryptPublicKey = ByteArray(8) { 0x03 },
            initiatorNonce = ByteArray(PairingV3.NONCE_SIZE) { 0x04 },
            supportedCiphersuites = listOf(PairingV3.CIPHERSUITE_SPAKE2_P256),
            signature = ByteArray(16) { 0x05 },
        )

    @Test
    fun testIntentRoundTrip() {
        val decoded = json.decodeFromString<PairingIntentV3>(json.encodeToString(intent))
        assertEquals(intent, decoded)
    }

    @Test
    fun testOfferRoundTrip() {
        val offer =
            PairingOfferV3(
                protocolVersion = PairingV3.PROTOCOL_VERSION,
                selectedCiphersuite = PairingV3.CIPHERSUITE_SPAKE2_P256,
                sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x11 },
                requestHash = ByteArray(32) { 0x12 },
                tokenGeneration = 1L,
                pinExpiresAt = 1_234_567_890L,
                initiatorAppInstanceId = "initiator-app",
                acceptorAppInstanceId = "acceptor-app",
                acceptorSignPublicKey = ByteArray(8) { 0x13 },
                acceptorCryptPublicKey = ByteArray(8) { 0x14 },
                acceptorNonce = ByteArray(PairingV3.NONCE_SIZE) { 0x15 },
                acceptorPakeShare = ByteArray(32) { 0x16 },
                signature = ByteArray(16) { 0x17 },
            )
        assertEquals(offer, json.decodeFromString<PairingOfferV3>(json.encodeToString(offer)))
    }

    @Test
    fun testProofRoundTrip() {
        val proof =
            PairingProofV3(
                sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x21 },
                tokenGeneration = 2L,
                initiatorPakeShare = ByteArray(32) { 0x22 },
                transcriptHash = ByteArray(32) { 0x23 },
                initiatorKeyConfirmation = ByteArray(32) { 0x24 },
                initiatorIdentitySignature = ByteArray(16) { 0x25 },
            )
        assertEquals(proof, json.decodeFromString<PairingProofV3>(json.encodeToString(proof)))
    }

    @Test
    fun testProofResponseRoundTrip() {
        val response =
            PairingProofResponseV3(
                sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x31 },
                transcriptHash = ByteArray(32) { 0x32 },
                acceptorKeyConfirmation = ByteArray(32) { 0x33 },
                acceptorIdentitySignature = ByteArray(16) { 0x34 },
            )
        assertEquals(response, json.decodeFromString<PairingProofResponseV3>(json.encodeToString(response)))
    }

    @Test
    fun testCommitAndAckRoundTrip() {
        val commit =
            PairingCommitV3(
                sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x41 },
                transcriptHash = ByteArray(32) { 0x42 },
                commitMac = ByteArray(32) { 0x43 },
            )
        assertEquals(commit, json.decodeFromString<PairingCommitV3>(json.encodeToString(commit)))

        val ack =
            PairingCommitAckV3(
                sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x51 },
                transcriptHash = ByteArray(32) { 0x52 },
                receiptMac = ByteArray(32) { 0x53 },
            )
        assertEquals(ack, json.decodeFromString<PairingCommitAckV3>(json.encodeToString(ack)))
    }

    @Test
    fun testCancelRoundTrip() {
        val cancel = PairingCancelV3(sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x61 })
        assertEquals(cancel, json.decodeFromString<PairingCancelV3>(json.encodeToString(cancel)))
    }

    @Test
    fun testErrorRoundTrip() {
        PairingV3ErrorCode.entries.forEach { code ->
            val error = PairingErrorV3(code)
            assertEquals(error, json.decodeFromString<PairingErrorV3>(json.encodeToString(error)))
        }
    }

    @Test
    fun testUnknownFieldsAreIgnoredForForwardCompatibility() {
        val jsonWithUnknownField =
            json.encodeToString(intent).dropLast(1) + ",\"injectedField\":\"value\"}"

        assertEquals(intent, json.decodeFromString<PairingIntentV3>(jsonWithUnknownField))
    }
}
