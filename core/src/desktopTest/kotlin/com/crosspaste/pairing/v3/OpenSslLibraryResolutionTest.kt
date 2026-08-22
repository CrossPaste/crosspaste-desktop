package com.crosspaste.pairing.v3

import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the libcrypto candidate resolution contract behind
 * [OpenSslPakeEcOps.load]: [LibCryptoResolution.Bundled] must resolve only the
 * packaged library (no silent fallback to an unreviewed system libcrypto),
 * [LibCryptoResolution.Environment] keeps the well-known candidates, and the
 * process-wide instance is pinned to the resolution it was loaded under.
 * Tests that depend on the absence of an override report themselves as
 * skipped (visibly, via JUnit assumptions) when the ambient environment sets
 * one.
 */
class OpenSslLibraryResolutionTest {

    private val overrideProperty = "crosspaste.libcrypto.path"

    private val bundled = LibCryptoResolution.Bundled("/opt/crosspaste/runtime/lib/libcrypto.so.3")

    private fun assumeNoAmbientOverride() {
        assumeTrue(
            "ambient libcrypto override set; candidate-order assertions do not apply",
            System.getProperty(overrideProperty) == null &&
                System.getenv("CROSSPASTE_LIBCRYPTO_PATH") == null,
        )
    }

    private fun withOverrideProperty(
        value: String,
        block: () -> Unit,
    ) {
        val previous = System.getProperty(overrideProperty)
        System.setProperty(overrideProperty, value)
        try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(overrideProperty)
            } else {
                System.setProperty(overrideProperty, previous)
            }
        }
    }

    @Test
    fun bundledPathIsTheOnlyCandidate() {
        assumeNoAmbientOverride()
        assertEquals(
            listOf(bundled.path),
            OpenSslPakeEcOps.libraryCandidates(bundled),
        )
    }

    @Test
    fun explicitOverrideWinsOverTheBundledPath() {
        withOverrideProperty("/tmp/custom-libcrypto.so") {
            assertEquals(
                listOf("/tmp/custom-libcrypto.so"),
                OpenSslPakeEcOps.libraryCandidates(bundled),
            )
        }
    }

    @Test
    fun environmentResolutionProbesWellKnownCandidates() {
        assumeNoAmbientOverride()
        val candidates = OpenSslPakeEcOps.libraryCandidates(LibCryptoResolution.Environment)
        // Every platform probes more than one well-known location in
        // development/test; none of them is an application-bundled path.
        assertTrue(candidates.size > 1)
    }

    @Test
    fun missingBundledLibraryFailsClosedWithoutSystemFallback() {
        assumeNoAmbientOverride()
        val missing = LibCryptoResolution.Bundled("/nonexistent/crosspaste/libcrypto.so.3")
        val exception =
            assertFailsWith<PakeException> {
                OpenSslPakeEcOps.loadLibCrypto(missing)
            }
        val message = exception.message.orEmpty()
        assertTrue(missing.path in message, "failure must name the bundled candidate: $message")
        // The bundled-mode message must not steer users toward installing a
        // system OpenSSL — production never falls back to it.
        assertFalse("brew" in message, "bundled-mode remedy leaked the dev hint: $message")
    }

    @Test
    fun failedOverrideRemedyPointsAtTheOverride() {
        withOverrideProperty("/nonexistent/override-libcrypto.so") {
            val exception =
                assertFailsWith<PakeException> {
                    OpenSslPakeEcOps.loadLibCrypto(bundled)
                }
            val message = exception.message.orEmpty()
            // The bundled library was never tried, so the remedy must point at
            // the override instead of suggesting a reinstall.
            assertTrue("override" in message, "remedy must call out the override: $message")
            assertFalse("reinstalling" in message, "remedy must not suggest a reinstall: $message")
        }
    }

    @Test
    fun aDifferentResolutionAfterTheFirstLoadFailsClosed() {
        // The desktop test suites load libcrypto from the environment (see
        // Spake2PakeProviderTest), so the process-wide instance is pinned to
        // Environment: a bundled-only request must not reuse it.
        OpenSslPakeEcOps.load()
        val exception =
            assertFailsWith<PakeException> {
                OpenSslPakeEcOps.load(bundled)
            }
        assertTrue("already loaded" in exception.message.orEmpty())
    }
}
