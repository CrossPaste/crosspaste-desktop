package com.crosspaste.pairing.v3

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PairingKeyFingerprintTest {

    @Test
    fun testCanonicalFingerprintIsFullSha256() =
        runTest {
            val fingerprint = PairingKeyFingerprint.of(ByteArray(32) { 1 })

            // Full 256-bit hash as 64 lowercase hex chars — truncating would leave
            // only 32-bit collision resistance for identity comparisons
            assertEquals(64, fingerprint.length)
            assertTrue(fingerprint.all { char -> char in '0'..'9' || char in 'a'..'f' })

            assertEquals(fingerprint, PairingKeyFingerprint.of(ByteArray(32) { 1 }))
            assertNotEquals(fingerprint, PairingKeyFingerprint.of(ByteArray(32) { 2 }))
        }

    @Test
    fun testDisplayIsShortPrefixOfCanonical() =
        runTest {
            val fingerprint = PairingKeyFingerprint.of(ByteArray(32) { 1 })
            val display = PairingKeyFingerprint.display(fingerprint)

            assertEquals(PairingKeyFingerprint.DISPLAY_LENGTH, display.length)
            assertTrue(fingerprint.startsWith(display))
        }
}
