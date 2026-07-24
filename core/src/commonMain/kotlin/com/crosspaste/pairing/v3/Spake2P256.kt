package com.crosspaste.pairing.v3

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.atomicfu.atomic

/**
 * Frozen constants and the protocol composition for SPAKE2 over P-256, RFC 9382
 * (ADR D1–D7). This layer performs NO elliptic-curve arithmetic; it decodes the
 * fixed M/N constants, derives `w` from the PIN, drives the SPAKE2 message flow,
 * assembles the RFC transcript TT, and extracts Ke — all group operations are
 * delegated to a [PakeEcOps] backend.
 */
object Spake2P256 {

    /**
     * RFC 9382 §6 constant M for P-256, compressed SEC1. Decoded to a curve point
     * once per session by the backend. Frozen wire constant.
     */
    const val M_COMPRESSED_HEX: String =
        "02886e2f97ace46e55ba9dd7242579f2993b64e16ef3dcab95afd497333d8fa12f"

    /** RFC 9382 §6 constant N for P-256, compressed SEC1. Frozen wire constant. */
    const val N_COMPRESSED_HEX: String =
        "03d8bbd6c639c62937b04d997f38c3770719c629d7014d49a24b4f98baa1292b49"

    /** HKDF `info` for the PIN-to-`w` derivation (ADR D4). Frozen. */
    const val W_INFO: String = "CrossPaste-v3-SPAKE2-w"

    /** Wide HKDF output length before reduction mod n (ADR D4): 48 bytes. */
    const val W_WIDE_LENGTH: Int = 48

    /** SPAKE2 Ke length for the SHA-256 suite: first half of Hash(TT), 16 bytes (RFC 9382 §4). */
    const val KE_LENGTH: Int = 16

    internal fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "odd-length hex" }
        return ByteArray(hex.length / 2) { index ->
            val hi = hex[index * 2].digitToInt(16)
            val lo = hex[index * 2 + 1].digitToInt(16)
            ((hi shl 4) or lo).toByte()
        }
    }
}

/**
 * SPAKE2/P-256 [PakeProvider], RFC 9382 (ADR D2 ciphersuite). Backed by a
 * platform [PakeEcOps]; the same protocol code runs on every platform and only
 * the EC backend differs. See [PakeProvider] for the frozen role/output contract.
 */
internal fun interface Spake2ScalarDeriver {

    suspend fun derive(
        pin: CharArray,
        pinContext: ByteArray,
    ): ByteArray
}

class Spake2PakeProvider internal constructor(
    private val ecOps: PakeEcOps,
    private val scalarDeriver: Spake2ScalarDeriver,
) : PakeProvider {

    constructor(ecOps: PakeEcOps) : this(ecOps, DefaultSpake2ScalarDeriver(ecOps))

    override val ciphersuite: String = PairingV3.CIPHERSUITE_SPAKE2_P256

    private val sha256 = CryptographyProvider.Default.get(SHA256)

    // The frozen M/N constants normalized to uncompressed form once per provider.
    private val mPoint: ByteArray by lazy { ecOps.toUncompressed(Spake2P256.hexToBytes(Spake2P256.M_COMPRESSED_HEX)) }
    private val nPoint: ByteArray by lazy { ecOps.toUncompressed(Spake2P256.hexToBytes(Spake2P256.N_COMPRESSED_HEX)) }

    override suspend fun createSession(
        role: PakeRole,
        pin: CharArray,
        context: PakeContext,
    ): PakeSession {
        val w = scalarDeriver.derive(pin, context.pinContext)
        var privateScalar: ByteArray? = null
        try {
            privateScalar = ecOps.randomScalar()
            // pA = w*M + x*G (initiator) or pB = w*N + y*G (acceptor).
            val ownConstant = if (role == PakeRole.INITIATOR) mPoint else nPoint
            var passwordElement: ByteArray? = null
            var ephemeralElement: ByteArray? = null
            val localShare =
                try {
                    passwordElement = ecOps.mulPoint(ownConstant, w)
                    ephemeralElement = ecOps.mulBase(privateScalar)
                    ecOps.addPoints(passwordElement, ephemeralElement)
                } finally {
                    passwordElement?.fill(0)
                    ephemeralElement?.fill(0)
                }
            return Spake2Session(
                role = role,
                ecOps = ecOps,
                hash = { data -> sha256.hasher().hash(data) },
                w = w,
                privateScalar = privateScalar,
                localShare = localShare,
                peerConstant = if (role == PakeRole.INITIATOR) nPoint else mPoint,
            )
        } catch (e: Throwable) {
            w.fill(0)
            privateScalar?.fill(0)
            throw e
        }
    }
}

private class DefaultSpake2ScalarDeriver(
    private val ecOps: PakeEcOps,
) : Spake2ScalarDeriver {

    private val sha256 = CryptographyProvider.Default.get(SHA256)

    /**
     * ADR D4: `w = OS2IP(HKDF-SHA256(salt = SHA-256(pinContext), IKM = UTF-8(pin),
     * info = "CrossPaste-v3-SPAKE2-w", L = 48)) mod n`. No memory-hard function —
     * the PIN is a one-time 30-second secret and SPAKE2 rules out offline grinding.
     */
    override suspend fun derive(
        pin: CharArray,
        pinContext: ByteArray,
    ): ByteArray {
        // Encode the PIN straight from the CharArray: the digits are ASCII, so no
        // intermediate immutable String (which could not be zeroed) is created.
        val pinBytes = ByteArray(pin.size) { index -> pin[index].code.toByte() }
        var salt: ByteArray? = null
        var prk: ByteArray? = null
        var wide: ByteArray? = null
        try {
            salt = sha256.hasher().hash(pinContext)
            prk = PairingKeySchedule.hkdfExtract(salt = salt, ikm = pinBytes)
            wide =
                PairingKeySchedule.hkdfExpand(
                    prk,
                    Spake2P256.W_INFO.encodeToByteArray(),
                    Spake2P256.W_WIDE_LENGTH,
                )
            val w = ecOps.reduceScalar(wide)
            // w == 0 would drop the M/N binding (0*M + x*G = x*G). It requires the
            // HKDF output to be a multiple of the group order (probability ~2^-256).
            if (w.all { byte -> byte.toInt() == 0 }) {
                w.fill(0)
                throw PakeException("degenerate SPAKE2 w")
            }
            return w
        } finally {
            pinBytes.fill(0)
            salt?.fill(0)
            prk?.fill(0)
            wide?.fill(0)
        }
    }
}

private class Spake2Session(
    private val role: PakeRole,
    private val ecOps: PakeEcOps,
    private val hash: suspend (ByteArray) -> ByteArray,
    private val w: ByteArray,
    private val privateScalar: ByteArray,
    private val localShare: ByteArray,
    /** N for the initiator, M for the acceptor — the constant to strip from the peer share. */
    private val peerConstant: ByteArray,
) : PakeSession {

    private val lifecycle = atomic(SESSION_ACTIVE)

    override suspend fun localShare(): ByteArray {
        check(lifecycle.value != SESSION_DESTROY_PENDING && lifecycle.value != SESSION_DESTROYED) {
            "session destroyed"
        }
        return localShare.copyOf()
    }

    override suspend fun deriveSharedSecret(peerShare: ByteArray): ByteArray {
        check(lifecycle.compareAndSet(SESSION_ACTIVE, SESSION_DERIVING)) {
            if (lifecycle.value == SESSION_DERIVING) {
                "session already deriving"
            } else {
                "session destroyed"
            }
        }
        if (peerShare.size != PairingV3.PAKE_SHARE_SIZE ||
            peerShare[0] != PairingV3.PAKE_SHARE_UNCOMPRESSED_PREFIX
        ) {
            finishDerivation()
            throw PakeException("SPAKE2 peer share must be a canonical uncompressed P-256 point")
        }

        var peerMask: ByteArray? = null
        var stripped: ByteArray? = null
        var k: ByteArray? = null
        var tt: ByteArray? = null
        var digest: ByteArray? = null
        try {
            // K = h * privateScalar * (peerShare - w * peerConstant); h = 1 for P-256.
            // ecOps validates peerShare lies on the curve and is not the identity.
            peerMask = ecOps.mulPoint(peerConstant, w)
            stripped = ecOps.subtractPoints(peerShare, peerMask)
            k = ecOps.mulPoint(stripped, privateScalar)

            // RFC 9382 §3.3 transcript with implicit (empty) identities. pA is
            // always the initiator share and pB the acceptor share.
            val (pa, pb) =
                when (role) {
                    PakeRole.INITIATOR -> localShare to peerShare
                    PakeRole.ACCEPTOR -> peerShare to localShare
                }
            tt =
                buildTranscript(
                    a = ByteArray(0),
                    b = ByteArray(0),
                    pa = pa,
                    pb = pb,
                    k = k,
                    w = w,
                )
            digest = hash(tt)
            // Ke is the first half of Hash(TT) (RFC 9382 §4); Ka is discarded.
            return digest.copyOf(Spake2P256.KE_LENGTH)
        } finally {
            peerMask?.fill(0)
            stripped?.fill(0)
            k?.fill(0)
            tt?.fill(0)
            digest?.fill(0)
            finishDerivation()
        }
    }

    override fun destroy() {
        while (true) {
            when (lifecycle.value) {
                SESSION_ACTIVE -> {
                    if (lifecycle.compareAndSet(SESSION_ACTIVE, SESSION_DESTROYED)) {
                        clearSecrets()
                        return
                    }
                }

                SESSION_DERIVING -> {
                    if (lifecycle.compareAndSet(SESSION_DERIVING, SESSION_DESTROY_PENDING)) {
                        return
                    }
                }

                SESSION_DESTROY_PENDING,
                SESSION_DESTROYED,
                -> return
            }
        }
    }

    private fun finishDerivation() {
        while (true) {
            when (lifecycle.value) {
                SESSION_DERIVING -> {
                    if (lifecycle.compareAndSet(SESSION_DERIVING, SESSION_ACTIVE)) {
                        return
                    }
                }

                SESSION_DESTROY_PENDING -> {
                    if (lifecycle.compareAndSet(SESSION_DESTROY_PENDING, SESSION_DESTROYED)) {
                        clearSecrets()
                        return
                    }
                }

                else -> return
            }
        }
    }

    private fun clearSecrets() {
        w.fill(0)
        privateScalar.fill(0)
    }

    /**
     * `TT = len(A)||A || len(B)||B || len(pA)||pA || len(pB)||pB || len(K)||K || len(w)||w`.
     *
     * Assembled into a single pre-sized [ByteArray] with no per-byte boxing, so the
     * secret-bearing K and w never leak into un-zeroable intermediate objects; the
     * caller zeroes the returned buffer.
     */
    private fun buildTranscript(
        a: ByteArray,
        b: ByteArray,
        pa: ByteArray,
        pb: ByteArray,
        k: ByteArray,
        w: ByteArray,
    ): ByteArray {
        val fields = arrayOf(a, b, pa, pb, k, w)
        val total = fields.sumOf { field -> LENGTH_PREFIX_SIZE + field.size }
        val out = ByteArray(total)
        var offset = 0
        for (field in fields) {
            writeLe64(out, offset, field.size)
            offset += LENGTH_PREFIX_SIZE
            field.copyInto(out, offset)
            offset += field.size
        }
        return out
    }

    /** Writes an eight-byte little-endian length prefix in place (RFC 9382 §3.3). */
    private fun writeLe64(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        val v = value.toLong()
        for (index in 0 until LENGTH_PREFIX_SIZE) {
            target[offset + index] = ((v ushr (index * 8)) and 0xFF).toByte()
        }
    }

    private companion object {
        const val LENGTH_PREFIX_SIZE = 8
        const val SESSION_ACTIVE = 0
        const val SESSION_DERIVING = 1
        const val SESSION_DESTROY_PENDING = 2
        const val SESSION_DESTROYED = 3
    }
}
