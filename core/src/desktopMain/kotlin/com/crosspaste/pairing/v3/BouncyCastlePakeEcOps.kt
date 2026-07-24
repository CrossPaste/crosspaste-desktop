package com.crosspaste.pairing.v3

import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.math.ec.FixedPointCombMultiplier
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import java.security.SecureRandom

/**
 * JVM/Android [PakeEcOps] backed by BouncyCastle low-level P-256 point/scalar ops.
 * This backend is retained for vectors and cross-platform conformance tests only;
 * it must not be registered as a production [PakeProvider].
 *
 * [FixedPointCombMultiplier] has a fixed loop and cache-safe lookup, but the
 * underlying P-256 point addition/doubling formulas contain input-dependent
 * branches. It therefore cannot satisfy RFC 9382 §7's requirement that every
 * elliptic-curve point operation take time independent of its inputs.
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

    override fun mulBase(scalar: ByteArray): ByteArray =
        FixedPointCombMultiplier().multiply(g, scalar.toScalar()).encodeUncompressed()

    override fun mulPoint(
        point: ByteArray,
        scalar: ByteArray,
    ): ByteArray =
        FixedPointCombMultiplier()
            .multiply(point.toPoint(), scalar.toScalar())
            .encodeUncompressed()

    override fun addPoints(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray = a.toPoint().add(b.toPoint()).encodeUncompressed()

    override fun subtractPoints(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray = a.toPoint().subtract(b.toPoint()).encodeUncompressed()

    override fun toUncompressed(point: ByteArray): ByteArray = point.toPoint().encodeUncompressed()

    private fun ByteArray.toScalar(): BigInteger {
        if (size != scalarSize) {
            throw PakeException("SPAKE2 scalar must be $scalarSize bytes")
        }
        val scalar = BigInteger(1, this)
        if (scalar.signum() <= 0 || scalar >= order) {
            throw PakeException("SPAKE2 scalar is outside the P-256 group order")
        }
        return scalar
    }

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
