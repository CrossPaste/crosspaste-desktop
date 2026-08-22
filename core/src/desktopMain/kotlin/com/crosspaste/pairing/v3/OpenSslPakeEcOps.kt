package com.crosspaste.pairing.v3

import com.sun.jna.IntegerType
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * How [OpenSslPakeEcOps.load] resolves the libcrypto to bind. Explicit overrides
 * apply only to [Environment]; [Bundled] always uses its reviewed packaged library.
 *
 * The whole process uses exactly one resolution: the first successful [load]
 * pins it, and a later call with a different resolution throws instead of
 * silently reusing a library the caller's policy would not have accepted.
 */
sealed interface LibCryptoResolution {

    /**
     * Development/test: probe the environment's well-known per-platform
     * locations (Homebrew, system libcrypto, ...).
     */
    data object Environment : LibCryptoResolution

    /**
     * Bundled builds (production/beta): resolve ONLY the libcrypto shipped
     * inside the application package at [path]. There is deliberately no
     * fallback to system paths — loading an unreviewed system libcrypto would
     * silently void the constant-time premise this backend is reviewed under,
     * so a missing bundled library fails closed (pairing v3 unavailable, v2
     * unaffected) instead.
     */
    data class Bundled(
        val path: String,
    ) : LibCryptoResolution
}

/**
 * JVM [PakeEcOps] backed by OpenSSL 3 libcrypto's low-level P-256 ops via JNA.
 *
 * This is the constant-time backend candidate for RFC 9382 §7 (final sign-off
 * rests with the independent security review):
 * - `EC_POINT_mul` performs a single fixed- or variable-point multiplication
 *   in constant time whenever the scalar lies in `[0, n)` — enforced here
 *   before every call via a branchless byte-level range check ([toScalar]),
 *   never via variable-time `BN_cmp` on a secret. The `ecp_nistz256`
 *   implementation handles point-formula special cases with branch-free
 *   conditional copies — the input-dependent branches that disqualified the
 *   previous BouncyCastle backend do not exist on this path.
 * - [reduceScalar] sets `BN_FLG_CONSTTIME` on the PIN-derived wide input so
 *   `BN_nnmod`'s division takes OpenSSL's no-branch path.
 *
 * Secret scalars live in OpenSSL `BIGNUM`s and are released with
 * `BN_clear_free`, which zeroes them before freeing — unlike the immutable
 * JVM `BigInteger` copies the BouncyCastle backend left behind for the GC.
 *
 * In [LibCryptoResolution.Environment], libcrypto is resolved from the
 * `crosspaste.libcrypto.path` system property or `CROSSPASTE_LIBCRYPTO_PATH`
 * environment variable first. [LibCryptoResolution.Bundled] ignores overrides and
 * loads only its packaged path. [load] fails fast with a [PakeException] when no
 * accepted candidate can be loaded.
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
                // The input is the PIN-derived HKDF output: force BN_nnmod's
                // division onto OpenSSL's no-branch constant-time path.
                lib.BN_set_flags(wide, BN_FLG_CONSTTIME)
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
            // clear_free, not free: intermediate points include secret group
            // elements (w·M, K, peerShare−w·M) whose coordinates would otherwise
            // survive in freed heap. ADR D5 requires K be non-recoverable; this
            // matches the BN_clear_free discipline used for secret scalars.
            lib.EC_POINT_clear_free(point)
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

    // Public group order bytes, cached for the branchless range check below.
    private val orderBytes: ByteArray = bnToFixedBytes(order)

    /**
     * Validates a (potentially secret) scalar into `[1, n)` — the precondition
     * for `EC_POINT_mul`'s constant-time path — using branchless byte
     * arithmetic instead of variable-time `BN_cmp`/`BN_is_zero`. The single
     * final branch only distinguishes valid from invalid, and an invalid
     * scalar aborts the handshake publicly anyway.
     */
    private fun toScalar(bytes: ByteArray): Pointer {
        if (bytes.size != scalarSize) {
            throw PakeException("SPAKE2 scalar must be $scalarSize bytes")
        }
        var borrow = 0
        var nonZero = 0
        for (i in bytes.indices.reversed()) {
            val byteValue = bytes[i].toInt() and 0xFF
            val diff = byteValue - (orderBytes[i].toInt() and 0xFF) - borrow
            borrow = (diff ushr 31) and 1
            nonZero = nonZero or byteValue
        }
        // borrow == 1 iff bytes < order (big-endian subtraction with borrow).
        if (borrow == 0 || nonZero == 0) {
            throw PakeException("SPAKE2 scalar is outside the P-256 group order")
        }
        return bnFromBytes(bytes)
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
        private const val BN_FLG_CONSTTIME = 0x04

        /** Every symbol [LibCrypto] binds; probed before a candidate is accepted. */
        private val REQUIRED_SYMBOLS =
            listOf(
                "EC_GROUP_new_by_curve_name",
                "EC_GROUP_get0_order",
                "EC_POINT_new",
                "EC_POINT_free",
                "EC_POINT_clear_free",
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
                "BN_set_flags",
                "BN_is_zero",
                "BN_priv_rand_range",
            )

        private val logger = KotlinLogging.logger {}

        private val lock = Any()

        // A failed load is not cached, so load() retries after e.g. the user
        // installs OpenSSL or sets the override. The first successful load pins
        // the process-wide instance TOGETHER with the resolution it was loaded
        // under, so the policy cannot be bypassed by call order.
        private var loaded: Pair<LibCryptoResolution, OpenSslPakeEcOps>? = null

        /**
         * Returns the process-wide ops instance (libcrypto loaded once), throwing
         * [PakeException] when no library candidate provides the full symbol set —
         * or when the instance was already loaded under a different [resolution].
         */
        fun load(resolution: LibCryptoResolution = LibCryptoResolution.Environment): OpenSslPakeEcOps =
            synchronized(lock) {
                loaded?.let { (loadedResolution, ops) ->
                    if (loadedResolution != resolution) {
                        throw PakeException(
                            "libcrypto is already loaded under $loadedResolution; " +
                                "refusing to reuse it under $resolution",
                        )
                    }
                    return ops
                }
                val (lib, libraryPath) = loadLibCrypto(resolution)
                val ops = OpenSslPakeEcOps(lib)
                // Startup diagnostic for release verification — logged only after
                // the binding AND the P-256 group setup succeeded, so grepping it
                // cannot produce a false green.
                logger.info { "pairing v3 libcrypto loaded from $libraryPath" }
                loaded = resolution to ops
                ops
            }

        internal fun loadLibCrypto(resolution: LibCryptoResolution): Pair<LibCrypto, String> {
            val candidates = libraryCandidates(resolution)
            val failures = mutableListOf<String>()
            for (candidate in candidates) {
                try {
                    // Reject libraries missing any required symbol up front (e.g.
                    // Apple's system LibreSSL lacks parts of the OpenSSL 1.1.1+
                    // BN/EC surface): fail fast here as a PakeException instead
                    // of an UnsatisfiedLinkError in the middle of an operation.
                    val library = NativeLibrary.getInstance(candidate)
                    REQUIRED_SYMBOLS.forEach(library::getFunction)
                    return Native.load(candidate, LibCrypto::class.java) to
                        (library.file?.absolutePath ?: candidate)
                } catch (ignored: UnsatisfiedLinkError) {
                    failures += candidate
                }
            }
            val remedy =
                when (resolution) {
                    is LibCryptoResolution.Bundled ->
                        "The bundled libcrypto is missing or unloadable; reinstalling the application should restore it."
                    LibCryptoResolution.Environment ->
                        if (overridePath() != null) {
                            "The explicit libcrypto override failed to load; fix it, or remove " +
                                "crosspaste.libcrypto.path / CROSSPASTE_LIBCRYPTO_PATH to restore the default resolution."
                        } else {
                            "Install OpenSSL 3 (e.g. brew install openssl@3) or point " +
                                "crosspaste.libcrypto.path / CROSSPASTE_LIBCRYPTO_PATH at a libcrypto with the full symbol set."
                        }
                }
            throw PakeException("OpenSSL 3 libcrypto not found; tried ${failures.joinToString()}. $remedy")
        }

        internal fun libraryCandidates(resolution: LibCryptoResolution): List<String> =
            when (resolution) {
                // Bundled = production/beta: resolve ONLY the packaged library. An
                // override is deliberately NOT honoured here — a shipped build binding
                // an attacker-set libcrypto would void the reviewed constant-time
                // premise. If one is set (troubleshooting habit, stale env var), warn
                // that it is ignored rather than silently switching libraries.
                is LibCryptoResolution.Bundled -> {
                    overridePath()?.let { override ->
                        logger.warn {
                            "ignoring libcrypto override '$override' in a bundled build; " +
                                "using the packaged library at ${resolution.path}"
                        }
                    }
                    listOf(resolution.path)
                }
                LibCryptoResolution.Environment ->
                    overridePath()?.let { listOf(it) } ?: platformEnvironmentCandidates()
            }

        private fun platformEnvironmentCandidates(): List<String> =
            when {
                Platform.isMac() ->
                    listOf(
                        "/opt/homebrew/opt/openssl@3/lib/libcrypto.3.dylib",
                        "/usr/local/opt/openssl@3/lib/libcrypto.3.dylib",
                        "crypto",
                    )
                Platform.isWindows() -> listOf("libcrypto-3-x64", "libcrypto-3", "libcrypto")
                else -> listOf("libcrypto.so.3", "crypto")
            }

        private fun overridePath(): String? =
            System.getProperty("crosspaste.libcrypto.path")
                ?: System.getenv("CROSSPASTE_LIBCRYPTO_PATH")
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

    fun EC_POINT_clear_free(point: Pointer)

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

    fun BN_set_flags(
        b: Pointer,
        n: Int,
    )

    fun BN_is_zero(a: Pointer): Int

    fun BN_priv_rand_range(
        r: Pointer,
        range: Pointer,
    ): Int
}
