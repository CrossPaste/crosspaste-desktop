package com.crosspaste.pairing.v3

import com.sun.jna.IntegerType
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer

/**
 * JVM [PakeEcOps] backed by OpenSSL 3 libcrypto's low-level P-256 ops via JNA.
 *
 * This is the reviewed constant-time backend RFC 9382 §7 requires:
 * `EC_POINT_mul` performs a single fixed- or variable-point multiplication in
 * constant time whenever the scalar lies in `[0, n)` (enforced here before
 * every call), and the `ecp_nistz256` implementation handles point-formula
 * special cases with branch-free conditional copies — the input-dependent
 * branches that disqualified the previous BouncyCastle backend do not exist
 * on this path.
 *
 * Secret scalars live in OpenSSL `BIGNUM`s and are released with
 * `BN_clear_free`, which zeroes them before freeing — unlike the immutable
 * JVM `BigInteger` copies the BouncyCastle backend left behind for the GC.
 *
 * libcrypto is resolved from the `crosspaste.libcrypto.path` system property
 * or `CROSSPASTE_LIBCRYPTO_PATH` environment variable first, then from
 * per-platform well-known library names. [load] fails fast with a
 * [PakeException] when no candidate can be loaded.
 */
class OpenSslPakeEcOps private constructor(
    private val lib: LibCrypto,
) : PakeEcOps {

    private val group: Pointer =
        lib.EC_GROUP_new_by_curve_name(NID_X9_62_PRIME256V1)
            ?: throw PakeException("libcrypto could not create the P-256 group")

    // Owned by the group and freed with it; never free directly.
    private val order: Pointer =
        lib.EC_GROUP_get0_order(group)
            ?: throw PakeException("libcrypto could not expose the P-256 group order")

    override val scalarSize: Int = P256_SCALAR_SIZE

    override fun reduceScalar(wideBigEndian: ByteArray): ByteArray =
        withBnCtx { ctx ->
            val wide = bnFromBytes(wideBigEndian)
            try {
                val reduced = lib.BN_new() ?: throw PakeException("libcrypto BN_new failed")
                try {
                    checkOne(lib.BN_nnmod(reduced, wide, order, ctx), "BN_nnmod")
                    bnToFixedBytes(reduced)
                } finally {
                    lib.BN_clear_free(reduced)
                }
            } finally {
                lib.BN_clear_free(wide)
            }
        }

    override fun randomScalar(): ByteArray {
        val scalar = lib.BN_new() ?: throw PakeException("libcrypto BN_new failed")
        try {
            repeat(MAX_RANDOM_ATTEMPTS) {
                checkOne(lib.BN_priv_rand_range(scalar, order), "BN_priv_rand_range")
                if (lib.BN_is_zero(scalar) == 0) {
                    return bnToFixedBytes(scalar)
                }
            }
            throw PakeException("libcrypto could not produce a non-zero scalar")
        } finally {
            lib.BN_clear_free(scalar)
        }
    }

    override fun mulBase(scalar: ByteArray): ByteArray =
        withBnCtx { ctx ->
            withScalar(scalar) { n ->
                withNewPoint { result ->
                    checkOne(lib.EC_POINT_mul(group, result, n, null, null, ctx), "EC_POINT_mul")
                    encodeUncompressed(result, ctx)
                }
            }
        }

    override fun mulPoint(
        point: ByteArray,
        scalar: ByteArray,
    ): ByteArray =
        withBnCtx { ctx ->
            withScalar(scalar) { m ->
                withPoint(point, ctx) { q ->
                    withNewPoint { result ->
                        checkOne(lib.EC_POINT_mul(group, result, null, q, m, ctx), "EC_POINT_mul")
                        encodeUncompressed(result, ctx)
                    }
                }
            }
        }

    override fun addPoints(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray =
        withBnCtx { ctx ->
            withPoint(a, ctx) { pa ->
                withPoint(b, ctx) { pb ->
                    withNewPoint { result ->
                        checkOne(lib.EC_POINT_add(group, result, pa, pb, ctx), "EC_POINT_add")
                        encodeUncompressed(result, ctx)
                    }
                }
            }
        }

    override fun subtractPoints(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray =
        withBnCtx { ctx ->
            withPoint(a, ctx) { pa ->
                withPoint(b, ctx) { pb ->
                    checkOne(lib.EC_POINT_invert(group, pb, ctx), "EC_POINT_invert")
                    withNewPoint { result ->
                        checkOne(lib.EC_POINT_add(group, result, pa, pb, ctx), "EC_POINT_add")
                        encodeUncompressed(result, ctx)
                    }
                }
            }
        }

    override fun toUncompressed(point: ByteArray): ByteArray =
        withBnCtx { ctx ->
            withPoint(point, ctx) { p ->
                encodeUncompressed(p, ctx)
            }
        }

    private inline fun <T> withBnCtx(block: (Pointer) -> T): T {
        val ctx = lib.BN_CTX_new() ?: throw PakeException("libcrypto BN_CTX_new failed")
        try {
            return block(ctx)
        } finally {
            lib.BN_CTX_free(ctx)
        }
    }

    private inline fun <T> withNewPoint(block: (Pointer) -> T): T {
        val point = lib.EC_POINT_new(group) ?: throw PakeException("libcrypto EC_POINT_new failed")
        try {
            return block(point)
        } finally {
            lib.EC_POINT_free(point)
        }
    }

    private inline fun <T> withPoint(
        encoded: ByteArray,
        ctx: Pointer,
        block: (Pointer) -> T,
    ): T =
        withNewPoint { point ->
            decodeInto(point, encoded, ctx)
            block(point)
        }

    private inline fun <T> withScalar(
        bytes: ByteArray,
        block: (Pointer) -> T,
    ): T {
        val scalar = toScalar(bytes)
        try {
            return block(scalar)
        } finally {
            lib.BN_clear_free(scalar)
        }
    }

    private fun decodeInto(
        point: Pointer,
        encoded: ByteArray,
        ctx: Pointer,
    ) {
        if (encoded.isEmpty() ||
            lib.EC_POINT_oct2point(group, point, encoded, sizeT(encoded.size), ctx) != 1
        ) {
            throw PakeException("invalid SPAKE2 point encoding")
        }
        if (lib.EC_POINT_is_on_curve(group, point, ctx) != 1 ||
            lib.EC_POINT_is_at_infinity(group, point) == 1
        ) {
            throw PakeException("SPAKE2 point is not a valid group element")
        }
    }

    private fun encodeUncompressed(
        point: Pointer,
        ctx: Pointer,
    ): ByteArray {
        if (lib.EC_POINT_is_at_infinity(group, point) == 1) {
            throw PakeException("SPAKE2 result is the identity element")
        }
        val out = ByteArray(uncompressedPointSize)
        val written =
            lib.EC_POINT_point2oct(
                group,
                point,
                POINT_CONVERSION_UNCOMPRESSED,
                out,
                sizeT(out.size),
                ctx,
            )
        if (written.toLong() != out.size.toLong()) {
            throw PakeException("libcrypto EC_POINT_point2oct failed")
        }
        return out
    }

    private fun toScalar(bytes: ByteArray): Pointer {
        if (bytes.size != scalarSize) {
            throw PakeException("SPAKE2 scalar must be $scalarSize bytes")
        }
        val scalar = bnFromBytes(bytes)
        if (lib.BN_is_zero(scalar) == 1 || lib.BN_cmp(scalar, order) >= 0) {
            lib.BN_clear_free(scalar)
            throw PakeException("SPAKE2 scalar is outside the P-256 group order")
        }
        return scalar
    }

    private fun bnFromBytes(bytes: ByteArray): Pointer =
        lib.BN_bin2bn(bytes, bytes.size, null)
            ?: throw PakeException("libcrypto BN_bin2bn failed")

    private fun bnToFixedBytes(bn: Pointer): ByteArray {
        val out = ByteArray(scalarSize)
        if (lib.BN_bn2binpad(bn, out, out.size) != out.size) {
            throw PakeException("libcrypto BN_bn2binpad failed")
        }
        return out
    }

    private fun checkOne(
        ret: Int,
        op: String,
    ) {
        if (ret != 1) {
            throw PakeException("libcrypto $op failed")
        }
    }

    private fun sizeT(value: Int): LibCryptoSizeT = LibCryptoSizeT(value.toLong())

    companion object {

        private const val NID_X9_62_PRIME256V1 = 415
        private const val POINT_CONVERSION_UNCOMPRESSED = 4
        private const val P256_SCALAR_SIZE = 32
        private const val MAX_RANDOM_ATTEMPTS = 128

        /** Every symbol [LibCrypto] binds; probed before a candidate is accepted. */
        private val REQUIRED_SYMBOLS =
            listOf(
                "EC_GROUP_new_by_curve_name",
                "EC_GROUP_get0_order",
                "EC_POINT_new",
                "EC_POINT_free",
                "EC_POINT_mul",
                "EC_POINT_add",
                "EC_POINT_invert",
                "EC_POINT_oct2point",
                "EC_POINT_point2oct",
                "EC_POINT_is_at_infinity",
                "EC_POINT_is_on_curve",
                "BN_new",
                "BN_clear_free",
                "BN_bin2bn",
                "BN_bn2binpad",
                "BN_CTX_new",
                "BN_CTX_free",
                "BN_nnmod",
                "BN_cmp",
                "BN_is_zero",
                "BN_priv_rand_range",
            )

        // A failed lazy initializer is not cached, so load() retries after e.g.
        // the user installs OpenSSL or sets the override.
        private val instance: OpenSslPakeEcOps by lazy { OpenSslPakeEcOps(loadLibCrypto()) }

        /**
         * Returns the process-wide ops instance (libcrypto loaded once), throwing
         * [PakeException] when no library candidate provides the full symbol set.
         */
        fun load(): OpenSslPakeEcOps = instance

        private fun loadLibCrypto(): LibCrypto {
            val failures = mutableListOf<String>()
            for (candidate in libraryCandidates()) {
                try {
                    // Reject libraries missing any required symbol up front (e.g.
                    // Apple's system LibreSSL lacks parts of the OpenSSL 1.1.1+
                    // BN/EC surface): fail fast here as a PakeException instead
                    // of an UnsatisfiedLinkError in the middle of an operation.
                    val library = NativeLibrary.getInstance(candidate)
                    REQUIRED_SYMBOLS.forEach(library::getFunction)
                    return Native.load(candidate, LibCrypto::class.java)
                } catch (ignored: UnsatisfiedLinkError) {
                    failures += candidate
                }
            }
            throw PakeException(
                "OpenSSL 3 libcrypto not found; tried ${failures.joinToString()}. " +
                    "Install OpenSSL 3 (e.g. brew install openssl@3) or point " +
                    "crosspaste.libcrypto.path / CROSSPASTE_LIBCRYPTO_PATH at a libcrypto with the full symbol set.",
            )
        }

        private fun libraryCandidates(): List<String> {
            val override =
                System.getProperty("crosspaste.libcrypto.path")
                    ?: System.getenv("CROSSPASTE_LIBCRYPTO_PATH")
            if (override != null) {
                return listOf(override)
            }
            return when {
                Platform.isMac() ->
                    listOf(
                        "/opt/homebrew/opt/openssl@3/lib/libcrypto.3.dylib",
                        "/usr/local/opt/openssl@3/lib/libcrypto.3.dylib",
                        "crypto",
                    )
                Platform.isWindows() -> listOf("libcrypto-3-x64", "libcrypto-3", "libcrypto")
                else -> listOf("libcrypto.so.3", "crypto")
            }
        }
    }
}

/** `size_t` for JNA calls; sized from [Native.SIZE_T_SIZE] at load time. */
internal class LibCryptoSizeT
    @JvmOverloads
    constructor(
        value: Long = 0,
    ) : IntegerType(Native.SIZE_T_SIZE, value, true) {

        override fun toByte(): Byte = toInt().toByte()

        override fun toShort(): Short = toInt().toShort()
    }

/**
 * The subset of OpenSSL 3 libcrypto used by [OpenSslPakeEcOps]. All symbols
 * are exported functions since OpenSSL 1.1.0 (opaque BIGNUM/EC types), so the
 * binding works against any 1.1.1/3.x libcrypto.
 */
@Suppress("FunctionName", "ktlint:standard:function-naming")
internal interface LibCrypto : Library {

    fun EC_GROUP_new_by_curve_name(nid: Int): Pointer?

    fun EC_GROUP_get0_order(group: Pointer): Pointer?

    fun EC_POINT_new(group: Pointer): Pointer?

    fun EC_POINT_free(point: Pointer)

    fun EC_POINT_mul(
        group: Pointer,
        r: Pointer,
        n: Pointer?,
        q: Pointer?,
        m: Pointer?,
        ctx: Pointer?,
    ): Int

    fun EC_POINT_add(
        group: Pointer,
        r: Pointer,
        a: Pointer,
        b: Pointer,
        ctx: Pointer?,
    ): Int

    fun EC_POINT_invert(
        group: Pointer,
        a: Pointer,
        ctx: Pointer?,
    ): Int

    fun EC_POINT_oct2point(
        group: Pointer,
        p: Pointer,
        buf: ByteArray,
        len: LibCryptoSizeT,
        ctx: Pointer?,
    ): Int

    fun EC_POINT_point2oct(
        group: Pointer,
        p: Pointer,
        form: Int,
        buf: ByteArray,
        len: LibCryptoSizeT,
        ctx: Pointer?,
    ): LibCryptoSizeT

    fun EC_POINT_is_at_infinity(
        group: Pointer,
        p: Pointer,
    ): Int

    fun EC_POINT_is_on_curve(
        group: Pointer,
        p: Pointer,
        ctx: Pointer?,
    ): Int

    fun BN_new(): Pointer?

    fun BN_clear_free(a: Pointer)

    fun BN_bin2bn(
        s: ByteArray,
        len: Int,
        ret: Pointer?,
    ): Pointer?

    fun BN_bn2binpad(
        a: Pointer,
        to: ByteArray,
        tolen: Int,
    ): Int

    fun BN_CTX_new(): Pointer?

    fun BN_CTX_free(ctx: Pointer)

    fun BN_nnmod(
        r: Pointer,
        a: Pointer,
        m: Pointer,
        ctx: Pointer,
    ): Int

    fun BN_cmp(
        a: Pointer,
        b: Pointer,
    ): Int

    fun BN_is_zero(a: Pointer): Int

    fun BN_priv_rand_range(
        r: Pointer,
        range: Pointer,
    ): Int
}
