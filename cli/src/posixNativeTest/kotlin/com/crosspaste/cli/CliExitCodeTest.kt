package com.crosspaste.cli

import com.github.ajalt.clikt.testing.test
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import platform.posix.setenv
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Pins the exit-code contract with an isolated HOME (no endpoint file →
 * app not running): non-interactive callers must fail fast with exit code 3,
 * never hang on input.
 */
@OptIn(ExperimentalForeignApi::class)
class CliExitCodeTest {

    private fun isolatedHome(): String {
        val dir =
            FileSystem.SYSTEM_TEMPORARY_DIRECTORY
                .resolve("cli-exit-code-test-${Random.nextBits(31)}")
        FileSystem.SYSTEM.createDirectories(dir)
        setenv("HOME", dir.toString(), 1)
        return dir.toString()
    }

    @Test
    fun commandFailsFastWithExitCodeThreeWhenAppNotRunning() {
        isolatedHome()
        val result = CrossPasteCommand().test("paste")
        assertEquals(3, result.statusCode)
        assertContains(result.stderr, "CrossPaste is not running")
    }

    @Test
    fun noStartSkipsThePromptAndExitsThree() {
        isolatedHome()
        val result = CrossPasteCommand().test("--no-start paste")
        assertEquals(3, result.statusCode)
        assertContains(result.stderr, "CrossPaste is not running")
    }

    @Test
    fun statusReportsNotRunningWithExitCodeThree() {
        isolatedHome()
        val result = CrossPasteCommand().test("status")
        assertEquals(3, result.statusCode)
        assertContains(result.output, "Not running")
    }

    @Test
    fun statusJsonReportsRunningFalse() {
        isolatedHome()
        val result = CrossPasteCommand().test("--json status")
        assertEquals(3, result.statusCode)
        assertContains(result.output, "\"running\": false")
        assertContains(result.output, "\"state\": \"not_running\"")
    }

    @Test
    fun rootHelpDocumentsTheExitCodeContract() {
        isolatedHome()
        val result = CrossPasteCommand().test("--help")
        assertEquals(0, result.statusCode)
        assertContains(result.output, "Exit codes: 0 success, 1 error, 2 usage error, 3 CrossPaste not running")
    }

    @Test
    fun noNewlineWithoutRawIsAUsageError() {
        isolatedHome()
        val result = CrossPasteCommand().test("paste --no-newline")
        assertContains(result.stderr, "--no-newline requires --raw")
    }
}
