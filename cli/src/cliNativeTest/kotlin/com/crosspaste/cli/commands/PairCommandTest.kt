package com.crosspaste.cli.commands

import com.crosspaste.cli.CrossPasteCommand
import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class PairCommandTest {

    // Pairing is a human confirmation (D-P6-3): a non-interactive stdin must
    // fail fast as a usage error instead of hanging on a code prompt. The
    // Clikt harness reports UsageError's raw status 1; main() remaps it to 2.

    @Test
    fun pairRefusesANonInteractiveTerminal() {
        val result = CrossPasteCommand().test("pair", inputInteractive = false)
        assertEquals(1, result.statusCode)
        assertContains(result.stderr, "pair requires an interactive terminal")
        // The usage error must carry the subcommand's context, not the root's
        assertContains(result.stderr, " pair [<options>]")
    }

    @Test
    fun pairWithTargetStillRequiresAnInteractiveTerminal() {
        val result = CrossPasteCommand().test("pair --target some-instance", inputInteractive = false)
        assertEquals(1, result.statusCode)
        assertContains(result.stderr, "pair requires an interactive terminal")
    }
}
