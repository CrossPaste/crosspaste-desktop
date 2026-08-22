package com.crosspaste.pairing.v3

import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the libcrypto candidate resolution contract behind
 * [OpenSslPakeEcOps.load]: bundled builds must resolve only the packaged
 * library (no silent fallback to an unreviewed system libcrypto), while
 * development/test environments keep the well-known candidates. Tests that
 * depend on the absence of an override report themselves as skipped (visibly,
 * via JUnit assumptions) when the ambient environment sets one.
 */
class OpenSslLibraryResolutionTest {

    private val overrideProperty = "crosspaste.libcrypto.path"

    private val bundledPath = "/opt/crosspaste/runtime/lib/libcrypto.so.3"

    private fun assumeNoAmbientOverride() {
        assumeTrue(
            "ambient libcrypto override set; candidate-order assertions do not apply",
            System.getProperty(overrideProperty) == null &&
                System.getenv("CROSSPASTE_LIBCRYPTO_PATH") == null,
        )
    }

    @Test
    fun bundledPathIsTheOnlyCandidate() {
        assumeNoAmbientOverride()
        assertEquals(
            listOf(bundledPath),
            OpenSslPakeEcOps.libraryCandidates(bundledPath),
        )
    }

    @Test
    fun explicitOverrideWinsOverTheBundledPath() {
        System.setProperty(overrideProperty, "/tmp/custom-libcrypto.so")
        try {
            assertEquals(
                listOf("/tmp/custom-libcrypto.so"),
                OpenSslPakeEcOps.libraryCandidates(bundledPath),
            )
        } finally {
            System.clearProperty(overrideProperty)
        }
    }

    @Test
    fun environmentCandidatesApplyOnlyWithoutABundledPath() {
        assumeNoAmbientOverride()
        val candidates = OpenSslPakeEcOps.libraryCandidates(null)
        // Every platform probes more than one well-known location in
        // development/test; none of them is an application-bundled path.
        assertTrue(candidates.size > 1)
    }

    @Test
    fun missingBundledLibraryFailsClosedWithoutSystemFallback() {
        assumeNoAmbientOverride()
        val missing = "/nonexistent/crosspaste/libcrypto.so.3"
        val exception =
            assertFailsWith<PakeException> {
                OpenSslPakeEcOps.loadLibCrypto(missing)
            }
        val message = exception.message.orEmpty()
        assertTrue(missing in message, "failure must name the bundled candidate: $message")
        // The bundled-mode message must not steer users toward installing a
        // system OpenSSL — production never falls back to it.
        assertFalse("brew" in message, "bundled-mode remedy leaked the dev hint: $message")
    }
}
