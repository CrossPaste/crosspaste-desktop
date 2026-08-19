package com.crosspaste.cli

import com.github.ajalt.clikt.testing.test
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import platform.posix.setenv
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the root command's no-subcommand behavior: piped stdin is an implicit
 * `copy`, an interactive terminal still gets a usage error (never a blocking
 * stdin read), and the single-letter aliases route to their commands.
 * A fake stdin reader keeps tests off the process's real stdin.
 */
@OptIn(ExperimentalForeignApi::class)
class RootCommandTest {

    private fun isolatedHome() {
        val dir =
            FileSystem.SYSTEM_TEMPORARY_DIRECTORY
                .resolve("cli-root-command-test-${Random.nextBits(31)}")
        FileSystem.SYSTEM.createDirectories(dir)
        setenv("HOME", dir.toString(), 1)
    }

    @Test
    fun pipedStdinWithNoCommandBehavesAsCopy() {
        isolatedHome()
        var stdinRead = false
        val result =
            CrossPasteCommand(stdinReader = {
                stdinRead = true
                "piped text"
            }).test("", inputInteractive = false)
        // stdin was consumed, then the (not running) app check failed with 3 —
        // the same path CopyStdinTest pins for an explicit `copy`
        assertTrue(stdinRead, "piped stdin must be read as an implicit copy")
        assertEquals(3, result.statusCode)
        assertContains(result.stderr, "CrossPaste is not running")
    }

    @Test
    fun interactiveTerminalWithNoCommandIsAUsageErrorAndNeverReadsStdin() {
        isolatedHome()
        var stdinRead = false
        val result =
            CrossPasteCommand(stdinReader = {
                stdinRead = true
                "unused"
            }).test("", inputInteractive = true)
        assertFalse(stdinRead, "must not block reading an interactive stdin")
        assertContains(result.stderr, "missing command")
    }

    @Test
    fun emptyPipedStdinWithNoCommandIsAUsageError() {
        isolatedHome()
        val result = CrossPasteCommand(stdinReader = { "" }).test("", inputInteractive = false)
        assertContains(result.stderr, "stdin was empty")
    }

    @Test
    fun aliasCRoutesToCopy() {
        isolatedHome()
        var stdinRead = false
        val result =
            CrossPasteCommand(stdinReader = {
                stdinRead = true
                "unused"
            }).test("c \"some text\"", inputInteractive = false)
        // The argument satisfies copy, so stdin stays untouched and the
        // command proceeds to the liveness check (app not running → 3)
        assertFalse(stdinRead)
        assertEquals(3, result.statusCode)
        assertContains(result.stderr, "CrossPaste is not running")
    }

    @Test
    fun aliasPRoutesToPaste() {
        isolatedHome()
        val result = CrossPasteCommand().test("p", inputInteractive = false)
        assertEquals(3, result.statusCode)
        assertContains(result.stderr, "CrossPaste is not running")
    }

    @Test
    fun aliasHRoutesToHistory() {
        isolatedHome()
        val result = CrossPasteCommand().test("h", inputInteractive = false)
        assertEquals(3, result.statusCode)
        assertContains(result.stderr, "CrossPaste is not running")
    }

    @Test
    fun unknownCommandIsStillAUsageError() {
        isolatedHome()
        val result = CrossPasteCommand().test("frobnicate", inputInteractive = false)
        // Must fail at parse time, not fall through to the implicit copy
        assertContains(result.stderr, "frobnicate")
        assertEquals(1, result.statusCode)
    }
}
