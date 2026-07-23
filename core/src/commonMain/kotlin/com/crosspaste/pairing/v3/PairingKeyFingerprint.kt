package com.crosspaste.pairing.v3

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * Signing-key fingerprint.
 *
 * [of] returns the FULL SHA-256 hex (64 chars) — this is the canonical identity
 * used for session deduplication and any security comparison. A truncated prefix
 * has only 32 bits of collision resistance and can be ground offline; it must
 * never gate a security decision. Use [display] only for rendering next to the
 * device name in UI.
 */
object PairingKeyFingerprint {

    private val sha256 = CryptographyProvider.Default.get(SHA256)

    const val DISPLAY_LENGTH: Int = 8

    /** Full SHA-256 hex of the signing public key: the canonical fingerprint. */
    suspend fun of(signPublicKey: ByteArray): String {
        val hash = sha256.hasher().hash(signPublicKey)
        return hash.joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    /** Short prefix for UI display only. Never compare identities with this. */
    fun display(fingerprint: String): String = fingerprint.take(DISPLAY_LENGTH)
}
