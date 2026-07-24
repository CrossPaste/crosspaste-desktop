package com.crosspaste.pairing.v3

/**
 * Narrow abstraction over the pairing v3 PAKE (SPAKE2, RFC 9382).
 *
 * ## Contract — FROZEN by the Phase 0 ADR (`ai/docs/PAIRING_V3_PHASE0_ADR.md`)
 *
 * 1. **Role mapping**: [PakeRole.INITIATOR] is RFC 9382 party A (constant M),
 *    [PakeRole.ACCEPTOR] is party B (constant N). Plain SPAKE2 with distinct
 *    M and N — never the M=N variant. Providers must not sort or otherwise
 *    symmetrize the roles; a role mismatch must fail key agreement.
 * 2. **PIN to w** (ADR D4): `w = OS2IP(HKDF-SHA256(salt = SHA-256(pinContext),
 *    IKM = UTF-8(pin digits), info = "CrossPaste-v3-SPAKE2-w", L = 48)) mod p`.
 *    No memory-hard function — the PIN is a one-time 30-second secret and
 *    SPAKE2 already rules out offline grinding.
 * 3. **Output** (ADR D5): [PakeSession.deriveSharedSecret] returns Ke, the
 *    shared secret produced through the RFC 9382 transcript TT. The raw group
 *    element K never crosses this boundary and must not be recoverable.
 * 4. **Key confirmation** (ADR D6): performed by the application layer — the
 *    v3 transcript-bound confirmation MACs in PairingProofV3 /
 *    PairingProofResponseV3 are the RFC-required key confirmation. Providers
 *    do NOT exchange cA/cB; this interface will not grow confirmation methods.
 * 5. **Implementation** (ADR D7): a thin, fully test-vectored protocol layer
 *    over reviewed EC primitives behind a narrow point-operations abstraction.
 *    Hand-rolled field or point arithmetic in application code is forbidden.
 *    BouncyCastle is retained for vectors only after the 2026-07-24 constant-time
 *    review; production platforms fail closed until reviewed backends land.
 */
interface PakeProvider {

    /** RFC 9382 ciphersuite identifier this provider implements. */
    val ciphersuite: String

    /**
     * Starts one PAKE session for one pairing session. The [pin] is the low-entropy
     * password; [context] binds the PAKE identities. Callers own [pin] clearing.
     */
    suspend fun createSession(
        role: PakeRole,
        pin: CharArray,
        context: PakeContext,
    ): PakeSession
}

enum class PakeRole {
    INITIATOR,
    ACCEPTOR,
}

/**
 * PAKE identity binding per RFC 9382.
 *
 * [pinContext] is the complete canonical output of
 * [PairingTranscriptCodec.encodePinContext]. Providers must use it exactly as the
 * D4 input when mapping the PIN to `w`; reconstructing a reduced context locally
 * would break cross-platform interoperability. Mutable inputs are copied on
 * construction and access so callers cannot change an in-flight PAKE session.
 */
class PakeContext(
    sessionId: ByteArray,
    val initiatorAppInstanceId: String,
    val acceptorAppInstanceId: String,
    pinContext: ByteArray,
) {

    private val sessionIdBytes = sessionId.copyOf()

    private val canonicalPinContext = pinContext.copyOf()

    val sessionId: ByteArray
        get() = sessionIdBytes.copyOf()

    val pinContext: ByteArray
        get() = canonicalPinContext.copyOf()

    init {
        require(sessionId.size == PairingV3.SESSION_ID_SIZE) {
            "session id must be ${PairingV3.SESSION_ID_SIZE} bytes"
        }
        require(pinContext.isNotEmpty()) { "canonical PIN context must not be empty" }
    }
}

interface PakeSession {

    /** The share to transmit to the peer for this role. */
    suspend fun localShare(): ByteArray

    /**
     * Consumes the peer share and derives the shared secret (Ke, contract §3).
     * Throws [PakeException] when the peer share is malformed or invalid.
     */
    suspend fun deriveSharedSecret(peerShare: ByteArray): ByteArray

    /** Clears internal secret state. The session is unusable afterwards. */
    fun destroy()
}

class PakeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
