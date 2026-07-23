package com.crosspaste.pairing.v3

import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import java.security.SecureRandom

/**
 * JVM/Android [PakeEcOps] backed by BouncyCastle low-level P-256 point/scalar ops
 * (ADR D7). No elliptic-curve arithmetic is written here — every operation is a
 * BouncyCastle primitive over the reviewed `secp256r1` implementation.
 *
 * Secret-hygiene limitation: scalars pass through immutable [BigInteger] inside
 * BouncyCastle, which cannot be zeroed; a `w`/private-scalar copy therefore lives
 * on the heap until GC. This is inherent to the library API and accepted — the
 * one-time 30-second PIN threat model does not warrant a custom scalar type.
 */
class BouncyCastlePakeEcOps(
    private val secureRandom: SecureRandom = SecureRandom(),
) : PakeEcOps {

    private val x9 = CustomNamedCurves.getByName("secp256r1")
    private val curve = x9.curve
    private val g: ECPoint = x9.g
    private val order: BigInteger = x9.n

    override val scalarSize: Int = (curve.fieldSize + 7) / 8

    override fun reduceScalar(wideBigEndian: ByteArray): ByteArray {
        // Reduce modulo the group ORDER n (x9.n), which is RFC 9382's "p" — the
        // prime order of the P-256 subgroup, not the field prime.
        val reduced = BigInteger(1, wideBigEndian).mod(order)
        return BigIntegers.asUnsignedByteArray(scalarSize, reduced)
    }

    override fun randomScalar(): ByteArray {
        val scalar = BigIntegers.createRandomInRange(BigInteger.ONE, order.subtract(BigInteger.ONE), secureRandom)
        return BigIntegers.asUnsignedByteArray(scalarSize, scalar)
    }

    override fun mulBase(scalar: ByteArray): ByteArray = g.multiply(scalar.toScalar()).encodeUncompressed()

    override fun mulPoint(
        point: ByteArray,
        scalar: ByteArray,
    ): ByteArray = point.toPoint().multiply(scalar.toScalar()).encodeUncompressed()

    override fun addPoints(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray = a.toPoint().add(b.toPoint()).encodeUncompressed()

    override fun subtractPoints(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray = a.toPoint().subtract(b.toPoint()).encodeUncompressed()

    override fun toUncompressed(point: ByteArray): ByteArray = point.toPoint().encodeUncompressed()

    private fun ByteArray.toScalar(): BigInteger = BigInteger(1, this)

    private fun ByteArray.toPoint(): ECPoint {
        val decoded =
            runCatching { curve.decodePoint(this) }
                .getOrElse { throw PakeException("invalid SPAKE2 point encoding", it) }
        if (decoded.isInfinity || !decoded.isValid) {
            throw PakeException("SPAKE2 point is not a valid group element")
        }
        return decoded
    }

    private fun ECPoint.encodeUncompressed(): ByteArray {
        val normalized = normalize()
        if (normalized.isInfinity) {
            throw PakeException("SPAKE2 result is the identity element")
        }
        return normalized.getEncoded(false)
    }
}
