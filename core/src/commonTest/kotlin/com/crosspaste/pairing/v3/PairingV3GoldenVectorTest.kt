package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingOfferV3
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Frozen protocol vectors for the REAL v3 messages: canonical intent/offer
 * encodings, PIN context and PIN, the full transcript, its hash, the HKDF key
 * schedule, and every confirmation MAC — computed over one fixed fixture chain.
 *
 * These bytes are the interoperability contract between desktop and mobile and
 * across releases: any codec, domain-string, field-id, HKDF-label, or
 * MAC-context change fails here. Running in commonTest, they are verified
 * byte-identical on JVM, JS, and native.
 */
class PairingV3GoldenVectorTest {

    private val intent =
        PairingIntentV3(
            protocolVersion = PairingV3.PROTOCOL_VERSION,
            requestId = ByteArray(PairingV3.REQUEST_ID_SIZE) { 0x01 },
            initiatorAppInstanceId = "initiator-app",
            targetAppInstanceId = "acceptor-app",
            initiatorDisplayName = "My Phone",
            initiatorSignPublicKey = ByteArray(32) { 0x11 },
            initiatorCryptPublicKey = ByteArray(32) { 0x22 },
            initiatorNonce = ByteArray(PairingV3.NONCE_SIZE) { 0x33 },
            supportedCiphersuites = listOf(PairingV3.CIPHERSUITE_SPAKE2_P256),
            signature = ByteArray(64) { 0x44 },
        )

    private suspend fun offer(): PairingOfferV3 =
        PairingOfferV3(
            protocolVersion = PairingV3.PROTOCOL_VERSION,
            selectedCiphersuite = PairingV3.CIPHERSUITE_SPAKE2_P256,
            sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x55 },
            requestHash = PairingTranscriptCodec.intentHash(intent),
            tokenGeneration = 1L,
            pinExpiresAt = 1_700_000_000_000L,
            initiatorAppInstanceId = "initiator-app",
            acceptorAppInstanceId = "acceptor-app",
            acceptorSignPublicKey = ByteArray(32) { 0x66 },
            acceptorCryptPublicKey = ByteArray(32) { 0x77 },
            acceptorNonce = ByteArray(PairingV3.NONCE_SIZE) { 0x88.toByte() },
            acceptorPakeShare = ByteArray(32) { 0x99.toByte() },
            signature = ByteArray(64) { 0xAA.toByte() },
        )

    private suspend fun transcript(): PairingTranscript {
        val offer = offer()
        return PairingTranscript(
            protocolVersion = PairingV3.PROTOCOL_VERSION,
            selectedCiphersuite = PairingV3.CIPHERSUITE_SPAKE2_P256,
            sessionId = offer.sessionId,
            tokenGeneration = 1L,
            initiatorAppInstanceId = "initiator-app",
            acceptorAppInstanceId = "acceptor-app",
            initiatorNonce = intent.initiatorNonce,
            acceptorNonce = offer.acceptorNonce,
            initiatorSignPublicKey = intent.initiatorSignPublicKey,
            initiatorCryptPublicKey = intent.initiatorCryptPublicKey,
            acceptorSignPublicKey = offer.acceptorSignPublicKey,
            acceptorCryptPublicKey = offer.acceptorCryptPublicKey,
            initiatorPakeShare = ByteArray(32) { 0xCC.toByte() },
            acceptorPakeShare = offer.acceptorPakeShare,
            intentHash = PairingTranscriptCodec.intentHash(intent),
            offerHash = PairingTranscriptCodec.offerHash(offer),
            negotiatedCapabilities = listOf("cap-a"),
        )
    }

    @Test
    fun testIntentSignaturePayloadVector() {
        assertEquals(
            "0000001c43726f737350617374652d50616972696e672d76332d496e74656e74" +
                "000000000400000003010000001001010101010101010101010101010101" +
                "020000000d696e69746961746f722d617070030000000c6163636570746f722d617070" +
                "04000000084d792050686f6e65" +
                "05000000201111111111111111111111111111111111111111111111111111111111111111" +
                "060000002022222222222222222222222222222222222222222222222222222222222222" +
                "220700000010333333333333333333333333333333330800000032000000010000002a" +
                "5350414b45322d503235362d5348413235362d484b44462d5348413235362d484d41432d534841323536",
            PairingTranscriptCodec.encodeIntentSignaturePayload(intent).toHexString(),
        )
    }

    @Test
    fun testIntentHashVector() =
        runTest {
            assertEquals(
                "0f9a583e33b56dc11c188e2ef3199ac55c1bea89dd27153e439eee34dcf193ba",
                PairingTranscriptCodec.intentHash(intent).toHexString(),
            )
        }

    @Test
    fun testOfferSignaturePayloadVector() =
        runTest {
            assertEquals(
                "0000001b43726f737350617374652d50616972696e672d76332d4f66666572" +
                    "000000000400000003010000002a" +
                    "5350414b45322d503235362d5348413235362d484b44462d5348413235362d484d41432d534841323536" +
                    "0200000010555555555555555555555555555555550300000020" +
                    "0f9a583e33b56dc11c188e2ef3199ac55c1bea89dd27153e439eee34dcf193ba" +
                    "0400000008000000000000000105000000080000018bcfe56800" +
                    "060000000d696e69746961746f722d617070070000000c6163636570746f722d617070" +
                    "08000000206666666666666666666666666666666666666666666666666666666666666666" +
                    "090000002077777777777777777777777777777777777777777777777777777777777777770a0000001088888888888888888888888888888888" +
                    "0b000000209999999999999999999999999999999999999999999999999999999999999999",
                PairingTranscriptCodec.encodeOfferSignaturePayload(offer()).toHexString(),
            )
        }

    @Test
    fun testOfferHashVector() =
        runTest {
            assertEquals(
                "a06c9f991f78406138b300f948496961ed918b411559b6877c1506020dc3d496",
                PairingTranscriptCodec.offerHash(offer()).toHexString(),
            )
        }

    @Test
    fun testPinContextAndPinVector() =
        runTest {
            val pinContext =
                PairingTranscriptCodec.encodePinContext(
                    sessionId = ByteArray(PairingV3.SESSION_ID_SIZE) { 0x55 },
                    tokenGeneration = 1L,
                    acceptorAppInstanceId = "acceptor-app",
                    initiatorAppInstanceId = "initiator-app",
                    acceptorSignPublicKey = ByteArray(32) { 0x66 },
                    acceptorCryptPublicKey = ByteArray(32) { 0x77 },
                    initiatorSignPublicKey = ByteArray(32) { 0x11 },
                    initiatorCryptPublicKey = ByteArray(32) { 0x22 },
                )
            assertEquals(
                "0000001943726f737350617374652d50616972696e672d76332d50494e" +
                    "0000000004000000030100000010555555555555555555555555555555550200000008" +
                    "0000000000000001030000000c6163636570746f722d617070040000000d696e69746961746f722d617070" +
                    "05000000206666666666666666666666666666666666666666666666666666666666666666" +
                    "060000002077777777777777777777777777777777777777777777777777777777777777770700000020" +
                    "111111111111111111111111111111111111111111111111111111111111111108000000202222222222222222222222222222222222222222222222222222222222222222",
                pinContext.toHexString(),
            )

            val pin = PairingPinGenerator.derivePin(ByteArray(PairingV3.PIN_SECRET_SIZE) { 0xBB.toByte() }, pinContext)
            assertEquals("620632", pin.concatToString())
        }

    @Test
    fun testTranscriptHashVector() =
        runTest {
            assertEquals(
                "19762d8c232bfbbab5a50741863f468b12f313a44f7e4ab7b7ccc8b4f846fdf3",
                PairingTranscriptCodec.transcriptHash(transcript()).toHexString(),
            )
        }

    @Test
    fun testKeyScheduleAndMacVectors() =
        runTest {
            val transcriptHash = PairingTranscriptCodec.transcriptHash(transcript())
            val keys = PairingKeySchedule.deriveSessionKeys(transcriptHash, ByteArray(32) { 0xDD.toByte() })

            assertEquals(
                "0f490d9974242f4b2c55b5151d33500b277628009bd044447766b7c63d4fda7d",
                keys.confirmInitiator.toHexString(),
            )
            assertEquals(
                "a4028730ce7201a1e10f726f2d3625e7709b30fc8e48057c6c864a1e40ab9fc3",
                keys.confirmAcceptor.toHexString(),
            )
            assertEquals(
                "3bbf8a39533c5f53532b5cf3839ee1b5ad5a9e35c607fb63a6d678f7ef4af872",
                keys.handshakeAead.toHexString(),
            )
            assertEquals(
                "46ea71f85ac78c467626a858385dd93ee7badcc07794fef5a41f29bab3200ace",
                keys.receipt.toHexString(),
            )

            assertEquals(
                "94bcca0048f9705686f9cc469a566bf90a1d913a8e6dec011ee7a4b05003edb4",
                PairingKeySchedule.initiatorConfirmation(keys, transcriptHash).toHexString(),
            )
            assertEquals(
                "0045ca697e367408d2418a59f51a10a5538f687ce18c5b75412a4c6a91c441a8",
                PairingKeySchedule.acceptorConfirmation(keys, transcriptHash).toHexString(),
            )
            assertEquals(
                "d0baeb77b1944a8d4f01d4ab6eceea608bf6be06b1e6c0ae28316e2bda1726b9",
                PairingKeySchedule.commitMac(keys, transcriptHash).toHexString(),
            )
            assertEquals(
                "f1a5905dcfa005d24b7787fc366802580a20b10e31ea77b1cf4524359b101b60",
                PairingKeySchedule.receiptMac(keys, transcriptHash).toHexString(),
            )
        }

    @Test
    fun testFullTranscriptEncodingVector() =
        runTest {
            assertEquals(
                "0000002043726f737350617374652d50616972696e672d76332d5472616e736372697074" +
                    "000000000400000003010000002a" +
                    "5350414b45322d503235362d5348413235362d484b44462d5348413235362d484d41432d534841323536" +
                    "0200000010555555555555555555555555555555550300000008000000000000000104" +
                    "0000000d696e69746961746f722d617070050000000c6163636570746f722d617070" +
                    "0600000010333333333333333333333333333333330700000010" +
                    "8888888888888888888888888888888808000000201111111111111111111111111111111111111111111111111111111111111111" +
                    "090000002022222222222222222222222222222222222222222222222222222222222222220a0000002066666666666666666666666666666666666666666666666666666666666666" +
                    "660b0000002077777777777777777777777777777777777777777777777777777777777777770c00000020" +
                    "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc" +
                    "0d000000209999999999999999999999999999999999999999999999999999999999999999" +
                    "0e000000200f9a583e33b56dc11c188e2ef3199ac55c1bea89dd27153e439eee34dcf193ba" +
                    "0f00000020a06c9f991f78406138b300f948496961ed918b411559b6877c1506020dc3d496" +
                    "100000000d00000001000000056361702d61",
                PairingTranscriptCodec.encodeTranscript(transcript()).toHexString(),
            )
        }
}
