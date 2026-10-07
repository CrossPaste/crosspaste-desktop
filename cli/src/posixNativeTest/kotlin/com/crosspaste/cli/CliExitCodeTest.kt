package com.crosspaste.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Pins the exit-code contract with an isolated HOME (no endpoint file →
 * app not running): non-interactive callers must fail fast with exit code 3,
 * never hang on input.
 */
class CliExitCodeTest {
    @Test
    fun commandFailsFastWithExitCodeThreeWhenAppNotRunning() {
        withIsolatedHome {
            val result = CrossPasteCommand().test("paste")
            assertEquals(3, result.statusCode)
            assertContains(result.stderr, "CrossPaste is not running")
        }
    }

    @Test
    fun noStartSkipsThePromptAndExitsThree() {
        withIsolatedHome {
            val result = CrossPasteCommand().test("--no-start paste")
            assertEquals(3, result.statusCode)
            assertContains(result.stderr, "CrossPaste is not running")
        }
    }

    @Test
    fun statusReportsNotRunningWithExitCodeThree() {
        withIsolatedHome {
            val result = CrossPasteCommand().test("status")
            assertEquals(3, result.statusCode)
            assertContains(result.output, "Not running")
        }
    }

    @Test
    fun statusJsonReportsRunningFalse() {
        withIsolatedHome {
            val result = CrossPasteCommand().test("--json status")
            assertEquals(3, result.statusCode)
            assertContains(result.output, "\"running\": false")
            assertContains(result.output, "\"state\": \"not_running\"")
        }
    }

    @Test
    fun rootHelpDocumentsTheExitCodeContract() {
        withIsolatedHome {
            val result = CrossPasteCommand().test("--help")
            assertEquals(0, result.statusCode)
            assertContains(result.output, "Exit codes: 0 success, 1 error, 2 usage error, 3 CrossPaste not running")
        }
    }

    @Test
    fun noNewlineWithoutAContentOnlyModeIsAUsageError() {
        withIsolatedHome {
            val result = CrossPasteCommand().test("paste --no-newline")
            assertContains(result.stderr, "--no-newline requires --raw or --summary")
            // The runtime UsageError carries the subcommand's context, so the
            // usage line shown is paste's, not the root command help
            assertContains(result.stderr, "Usage: crosspaste paste")
        }
    }

    @Test
    fun rawAndSummaryAreMutuallyExclusive() {
        withIsolatedHome {
            val result = CrossPasteCommand().test("paste --raw --summary")
            assertContains(result.stderr, "--raw and --summary are mutually exclusive")
            assertContains(result.stderr, "Usage: crosspaste paste")
        }
    }
}
