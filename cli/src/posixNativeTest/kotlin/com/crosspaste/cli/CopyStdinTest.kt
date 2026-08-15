package com.crosspaste.cli

import com.crosspaste.cli.commands.CopyCommand
import com.crosspaste.cli.platform.StdinReadException
import com.crosspaste.cli.platform.StdinTooLargeException
import com.github.ajalt.clikt.testing.test
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import platform.posix.setenv
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pins the copy command's stdin behavior: with no argument it reads piped
 * stdin, refuses to hang on an interactive terminal, and rejects empty input.
 * A fake stdin reader keeps tests off the process's real stdin.
 */
@OptIn(ExperimentalForeignApi::class)
class CopyStdinTest {

    private fun isolatedHome() {
        val dir =
            FileSystem.SYSTEM_TEMPORARY_DIRECTORY
                .resolve("cli-copy-stdin-test-${Random.nextBits(31)}")
        FileSystem.SYSTEM.createDirectories(dir)
        setenv("HOME", dir.toString(), 1)
    }

    @Test
    fun interactiveStdinWithoutArgumentIsAUsageError() {
        isolatedHome()
        var stdinRead = false
        val result =
            CopyCommand(stdinReader = {
                stdinRead = true
                "unused"
            }).test("", inputInteractive = true)
        // The Clikt test harness reports UsageError's raw status code (1);
        // the real binary remaps usage errors to exit code 2 in main()
        assertContains(result.stderr, "provide text as an argument")
        // The error carries this command's context: its own usage, not root help
        assertContains(result.stderr, "Usage: copy")
        assertFalse(stdinRead, "must not block reading an interactive stdin")
    }

    @Test
    fun pipedStdinIsReadAndTheCommandProceedsToTheLivenessCheck() {
        isolatedHome()
        var stdinRead = false
        val result =
            CopyCommand(stdinReader = {
                stdinRead = true
                "piped text"
            }).test("", inputInteractive = false)
        // stdin was consumed, then the (not running) app check failed with 3
        assertEquals(true, stdinRead)
        assertEquals(3, result.statusCode)
        assertContains(result.stderr, "CrossPaste is not running")
    }

    @Test
    fun emptyPipedStdinIsAUsageError() {
        isolatedHome()
        val result = CopyCommand(stdinReader = { "" }).test("", inputInteractive = false)
        assertContains(result.stderr, "stdin was empty")
    }

    @Test
    fun oversizedStdinFailsWithAClearError() {
        isolatedHome()
        val result =
            CopyCommand(stdinReader = { throw StdinTooLargeException() })
                .test("", inputInteractive = false)
        assertEquals(1, result.statusCode)
        assertContains(result.stderr, "MiB limit")
    }

    @Test
    fun stdinReadErrorFailsWithExitOneInsteadOfCopyingTruncatedContent() {
        isolatedHome()
        val result =
            CopyCommand(stdinReader = { throw StdinReadException("failed to read stdin: Bad file descriptor") })
                .test("", inputInteractive = false)
        assertEquals(1, result.statusCode)
        assertContains(result.stderr, "failed to read stdin")
    }

    @Test
    fun argumentTakesPrecedenceOverStdin() {
        isolatedHome()
        var stdinRead = false
        val result =
            CopyCommand(stdinReader = {
                stdinRead = true
                "unused"
            }).test("\"some text\"", inputInteractive = false)
        assertFalse(stdinRead, "an explicit argument must not read stdin")
        // Proceeds to the liveness check, which fails with the app stopped
        assertEquals(3, result.statusCode)
    }
}
