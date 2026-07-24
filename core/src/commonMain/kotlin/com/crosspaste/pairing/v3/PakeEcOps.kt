package com.crosspaste.pairing.v3

/**
 * Narrow P-256 elliptic-curve boundary for the SPAKE2 protocol layer (ADR D7).
 *
 * The SPAKE2 composition in [Spake2Protocol] performs NO field or point
 * arithmetic itself; every group operation goes through this interface, backed by
 * a reviewed library per platform (BouncyCastle on JVM/Android, OpenSSL 3 on
 * Kotlin/Native). Points cross this boundary as SEC1 **uncompressed** encodings
 * (`0x04 || X || Y`, 65 bytes for P-256); scalars cross as big-endian byte arrays
 * padded to [scalarSize].
 *
 * Implementations MUST reject the identity/point-at-infinity and any input not on
 * the curve — SPAKE2 security depends on peer-share validation.
 */
interface PakeEcOps {

    /** Size in bytes of a scalar / field coordinate for this curve (32 for P-256). */
    val scalarSize: Int

    /** Uncompressed point length: `1 + 2 * scalarSize` (65 for P-256). */
    val uncompressedPointSize: Int
        get() = 1 + 2 * scalarSize

    /**
     * Reduces a wide big-endian byte string modulo the group order n, returning a
     * big-endian scalar padded to [scalarSize]. Used for the SPAKE2 `w` (48 bytes
     * of HKDF output reduced mod n, ADR D4). The result may be zero only if the
     * input reduces to zero, which the protocol layer treats as a hard failure.
     */
    fun reduceScalar(wideBigEndian: ByteArray): ByteArray

    /** A cryptographically secure random scalar in `[1, n)`, big-endian, [scalarSize] bytes. */
    fun randomScalar(): ByteArray

    /** `scalar * G` (the standard base point), returned uncompressed. */
    fun mulBase(scalar: ByteArray): ByteArray

    /**
     * `scalar * point`, returned uncompressed. Protocol callers pass uncompressed
     * peer shares; implementations may also accept compressed encodings here so
     * the frozen compressed M/N constants can be normalized internally.
     * Implementations validate the point lies on the curve and is not the
     * identity, throwing [PakeException] otherwise.
     */
    fun mulPoint(
        point: ByteArray,
        scalar: ByteArray,
    ): ByteArray

    /** Point addition `a + b`, returned uncompressed. Both inputs are validated. */
    fun addPoints(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray

    /** Point subtraction `a - b`, returned uncompressed. Both inputs are validated. */
    fun subtractPoints(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray

    /**
     * Re-encodes any valid SEC1 point (compressed or uncompressed) to the
     * uncompressed form, validating it lies on the curve and is not the identity.
     * Used to normalize the frozen compressed M/N constants once.
     */
    fun toUncompressed(point: ByteArray): ByteArray
}
