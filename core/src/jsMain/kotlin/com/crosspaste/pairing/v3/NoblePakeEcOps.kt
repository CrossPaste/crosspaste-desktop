package com.crosspaste.pairing.v3

import dev.whyoleg.cryptography.random.CryptographyRandom
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

/**
 * JS/browser [PakeEcOps] backed by the audited `@noble/curves` P-256
 * implementation (ADR D7: reviewed library per platform — BouncyCastle on
 * JVM/Android, OpenSSL 3 on Kotlin/Native, noble on JS). No hand-rolled EC
 * arithmetic; every group operation delegates to `p256.ProjectivePoint`, which
 * validates curve membership on decode and enforces `1 <= scalar < n` on
 * multiplication.
 *
 * Like the BouncyCastle backend, this is NOT constant-time: noble's point
 * formulas contain input-dependent branches, and scalars pass through immutable
 * JS `BigInt`s that cannot be zeroed. Accepted for the one-time 30-second PIN
 * threat model; the RFC 9382 §7 constant-time requirement remains the gate for
 * flipping production `PAIRING_VERSION` to 3, not for this client backend.
 */
class NoblePakeEcOps : PakeEcOps {

    private val point: dynamic = p256.ProjectivePoint
    private val order: dynamic = p256.CURVE.n

    private val bigOne: dynamic = js("BigInt(1)")
    private val bigZero: dynamic = js("BigInt(0)")

    override val scalarSize: Int = 32

    override fun reduceScalar(wideBigEndian: ByteArray): ByteArray {
        val wide: dynamic = bigIntFromBytes(wideBigEndian)
        val reduced = wide % order
        return bigIntToFixedBytes(reduced, scalarSize)
    }

    override fun randomScalar(): ByteArray {
        // Wide reduction (2^384 mod n) keeps the modulo bias below 2^-128,
        // matching the SPAKE2 w derivation width.
        while (true) {
            val candidate = reduceScalar(CryptographyRandom.nextBytes(Spake2P256.W_WIDE_LENGTH))
            if (candidate.any { byte -> byte.toInt() != 0 }) {
                return candidate
            }
        }
    }

    override fun mulBase(scalar: ByteArray): ByteArray {
        val k = toScalar(scalar)
        return guard("mulBase") {
            encodeUncompressed(point.BASE.multiply(k))
        }
    }

    override fun mulPoint(
        point: ByteArray,
        scalar: ByteArray,
    ): ByteArray {
        val k = toScalar(scalar)
        val p = decodePoint(point)
        return guard("mulPoint") {
            encodeUncompressed(p.multiply(k))
        }
    }

    override fun addPoints(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray {
        val pa = decodePoint(a)
        val pb = decodePoint(b)
        return guard("addPoints") {
            encodeUncompressed(pa.add(pb))
        }
    }

    override fun subtractPoints(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray {
        val pa = decodePoint(a)
        val pb = decodePoint(b)
        return guard("subtractPoints") {
            encodeUncompressed(pa.subtract(pb))
        }
    }

    override fun toUncompressed(point: ByteArray): ByteArray = encodeUncompressed(decodePoint(point))

    private fun toScalar(bytes: ByteArray): dynamic {
        if (bytes.size != scalarSize) {
            throw PakeException("SPAKE2 scalar must be $scalarSize bytes")
        }
        val scalar: dynamic = bigIntFromBytes(bytes)
        val inRange = scalar >= bigOne && scalar < order
        if (!(inRange as Boolean)) {
            throw PakeException("SPAKE2 scalar is outside the P-256 group order")
        }
        return scalar
    }

    private fun decodePoint(encoded: ByteArray): dynamic {
        val decoded =
            guard("invalid SPAKE2 point encoding") {
                point.fromHex(encoded.toUint8Array())
            }
        if (isIdentity(decoded)) {
            throw PakeException("SPAKE2 point is not a valid group element")
        }
        return decoded
    }

    private fun encodeUncompressed(p: dynamic): ByteArray {
        if (isIdentity(p)) {
            throw PakeException("SPAKE2 result is the identity element")
        }
        val raw =
            guard("point encoding failed") {
                p.toRawBytes(false).unsafeCast<Uint8Array>()
            }
        return raw.toByteArray()
    }

    private fun isIdentity(p: dynamic): Boolean = p.equals(point.ZERO) as Boolean

    private inline fun <T> guard(
        context: String,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (e: PakeException) {
            throw e
        } catch (e: Throwable) {
            throw PakeException("$context: ${e.message}", e)
        }

    private fun bigIntFromBytes(bytes: ByteArray): dynamic {
        if (bytes.isEmpty()) {
            return bigZero
        }
        val hex =
            bytes.joinToString("") { byte ->
                (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
            }
        return js("BigInt")("0x$hex")
    }

    private fun bigIntToFixedBytes(
        value: dynamic,
        size: Int,
    ): ByteArray {
        val hex = (value.toString(16) as String).padStart(size * 2, '0')
        if (hex.length > size * 2) {
            throw PakeException("scalar exceeds $size bytes")
        }
        return ByteArray(size) { index ->
            val hi = hex[index * 2].digitToInt(16)
            val lo = hex[index * 2 + 1].digitToInt(16)
            ((hi shl 4) or lo).toByte()
        }
    }

    private fun ByteArray.toUint8Array(): Uint8Array {
        val int8 = unsafeCast<Int8Array>()
        return Uint8Array(int8.buffer, int8.byteOffset, int8.length)
    }

    private fun Uint8Array.toByteArray(): ByteArray =
        Int8Array(buffer, byteOffset, length).unsafeCast<ByteArray>().copyOf()
}
