package com.crosspaste.cli.platform

import com.crosspaste.cli.api.CLI_IMAGE_MAX_BOX_PX
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalProbeTest {

    private val esc = 27.toChar().toString()

    private fun env(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get

    // Windows Terminal >= 1.22 style DA1 reply (attribute 4 = sixel)
    private val wtSixelReply = "$esc[?61;4;6;7;14;21;22;23;24;28;32;42c"

    // Windows Terminal < 1.22 style DA1 reply: same class, no attribute 4
    private val wtNoSixelReply = "$esc[?61;6;7;14;21;22;23;24;28;32;42c"

    private val cellSizeReply = "$esc[6;20;10t"

    @Test
    fun sixelCandidateMatrix() {
        assertTrue(isSixelCandidate(env("WT_SESSION" to "guid", "TERM" to "xterm-256color")))
        assertTrue(isSixelCandidate(env("TERM" to "foot")))
        assertTrue(isSixelCandidate(env("TERM" to "foot-extra")))
        assertTrue(isSixelCandidate(env("TERM" to "mlterm")))
        assertTrue(isSixelCandidate(env("TERM" to "yaft-256color")))
        // conhost: no WT_SESSION, no sixel TERM
        assertFalse(isSixelCandidate(env("TERM" to "xterm-256color")))
        assertFalse(isSixelCandidate(env()))
        // The multiplexer guard outranks every candidate signal
        assertFalse(isSixelCandidate(env("WT_SESSION" to "guid", "TMUX" to "/tmp/tmux-1")))
        assertFalse(isSixelCandidate(env("TERM" to "screen.foot")))
        assertFalse(isSixelCandidate(env("TERM" to "tmux-256color", "WT_SESSION" to "guid")))
    }

    @Test
    fun da1ReplyCompletionDetection() {
        assertFalse(containsDa1Reply(""))
        assertFalse(containsDa1Reply("$esc[?61;4"))
        // The cell-size reply alone does not end the transaction
        assertFalse(containsDa1Reply(cellSizeReply))
        assertTrue(containsDa1Reply(wtSixelReply))
        assertTrue(containsDa1Reply(cellSizeReply + wtSixelReply))
    }

    @Test
    fun da1SixelAttributeParsing() {
        assertTrue(parseDa1SixelSupport(wtSixelReply))
        assertTrue(parseDa1SixelSupport("$esc[?63;1;2;3;4;7;29c")) // mlterm style
        assertFalse(parseDa1SixelSupport(wtNoSixelReply))
        assertFalse(parseDa1SixelSupport("$esc[?1;2c")) // VT100
        assertFalse(parseDa1SixelSupport(""))
        assertFalse(parseDa1SixelSupport("garbage"))
        // A lone first parameter is the device class, never an attribute
        assertFalse(parseDa1SixelSupport("$esc[?4c"))
        // Attribute must match exactly, not as a substring
        assertFalse(parseDa1SixelSupport("$esc[?62;14;42c"))
    }

    @Test
    fun cellSizeReplyParsing() {
        // Reply order is height;width — the parsed pair is (width, height)
        assertEquals(10 to 20, parseCellSizeReply(cellSizeReply))
        assertEquals(10 to 20, parseCellSizeReply(cellSizeReply + wtSixelReply))
        assertNull(parseCellSizeReply(""))
        assertNull(parseCellSizeReply(wtSixelReply))
        assertNull(parseCellSizeReply("$esc[6;20t"))
        assertNull(parseCellSizeReply("$esc[6;0;10t"))
        assertNull(parseCellSizeReply("$esc[6;20;-1t"))
        assertNull(parseCellSizeReply("$esc[6;9999;10t"))
        assertNull(parseCellSizeReply("$esc[6;a;bt"))
    }

    private class RecordingQuery(
        private val reply: String?,
    ) {
        var calls = 0
        var lastPayload: String? = null

        fun query(
            payload: String,
            timeoutMillis: Int,
            isComplete: (String) -> Boolean,
        ): String? {
            calls++
            lastPayload = payload
            check(timeoutMillis > 0) { "probe must carry a positive timeout" }
            // A real transaction would have stopped reading exactly here
            reply?.let { check(isComplete(it) || !it.contains('c')) }
            return reply
        }
    }

    private fun resolve(
        environment: (String) -> String?,
        query: RecordingQuery,
        stdinInteractive: Boolean = true,
        stdoutInteractive: Boolean = true,
    ): TerminalImageDisplay? =
        resolveTerminalImageDisplay(
            stdinInteractive = stdinInteractive,
            stdoutInteractive = stdoutInteractive,
            env = environment,
            query = query::query,
        )

    @Test
    fun knownProtocolsResolveWithoutProbing() {
        val query = RecordingQuery(reply = wtSixelReply)
        val display = resolve(env("TERM" to "xterm-kitty"), query)
        assertEquals(TerminalImageProtocol.KITTY, display?.protocol)
        assertEquals(0, query.calls, "kitty/iterm terminals must never be probed")
    }

    @Test
    fun windowsTerminalWithSixelConfirmsAndParsesCellSize() {
        val query = RecordingQuery(reply = cellSizeReply + wtSixelReply)
        val display = resolve(env("WT_SESSION" to "guid"), query)
        assertEquals(TerminalImageProtocol.SIXEL, display?.protocol)
        assertEquals(10, display?.cellWidthPx)
        assertEquals(20, display?.cellHeightPx)
        assertEquals(1, query.calls)
        assertEquals(SIXEL_PROBE_PAYLOAD, query.lastPayload)
    }

    @Test
    fun unansweredCellSizeQueryFallsBackToDefaultCell() {
        val query = RecordingQuery(reply = wtSixelReply)
        val display = resolve(env("WT_SESSION" to "guid"), query)
        assertEquals(TerminalImageProtocol.SIXEL, display?.protocol)
        assertEquals(DEFAULT_CELL_WIDTH_PX, display?.cellWidthPx)
        assertEquals(DEFAULT_CELL_HEIGHT_PX, display?.cellHeightPx)
    }

    @Test
    fun oldWindowsTerminalWithoutSixelAttributeFallsBack() {
        val query = RecordingQuery(reply = wtNoSixelReply)
        assertNull(resolve(env("WT_SESSION" to "guid"), query))
        assertEquals(1, query.calls)
    }

    @Test
    fun probeTimeoutOrRawModeFailureFallsBack() {
        assertNull(resolve(env("WT_SESSION" to "guid"), RecordingQuery(reply = "")))
        assertNull(resolve(env("WT_SESSION" to "guid"), RecordingQuery(reply = null)))
    }

    @Test
    fun nonInteractiveStreamsAreNeverProbed() {
        val query = RecordingQuery(reply = wtSixelReply)
        assertNull(resolve(env("WT_SESSION" to "guid"), query, stdinInteractive = false))
        assertNull(resolve(env("WT_SESSION" to "guid"), query, stdoutInteractive = false))
        assertEquals(0, query.calls)
    }

    @Test
    fun unknownTerminalIsNeverProbed() {
        val query = RecordingQuery(reply = wtSixelReply)
        assertNull(resolve(env("TERM" to "xterm-256color"), query))
        assertEquals(0, query.calls)
    }

    // region sixelPixelBox

    private val defaultCellDisplay = TerminalImageDisplay(TerminalImageProtocol.SIXEL)

    @Test
    fun pixelBoxUsesTheDefaultCellWhenTheProbeGaveNone() {
        // pick's panel box (60 columns x 9 rows) under the 10x20 assumption
        assertEquals(600 to 180, sixelPixelBox(60, 9, defaultCellDisplay))
    }

    @Test
    fun pixelBoxUsesProbedCellMetrics() {
        val probed =
            TerminalImageDisplay(
                TerminalImageProtocol.SIXEL,
                cellWidthPx = 8,
                cellHeightPx = 16,
            )
        assertEquals(480 to 144, sixelPixelBox(60, 9, probed))
        // A large probed cell saturates at the endpoint clamp per axis
        val big =
            TerminalImageDisplay(
                TerminalImageProtocol.SIXEL,
                cellWidthPx = 512,
                cellHeightPx = 512,
            )
        assertEquals(CLI_IMAGE_MAX_BOX_PX to CLI_IMAGE_MAX_BOX_PX, sixelPixelBox(60, 9, big))
    }

    @Test
    fun pixelBoxClampsToTheEndpointBoxAndNeverOverflows() {
        // A huge COLUMNS override must saturate, not wrap negative
        assertEquals(
            CLI_IMAGE_MAX_BOX_PX to 180,
            sixelPixelBox(Int.MAX_VALUE, 9, defaultCellDisplay),
        )
        assertEquals(
            CLI_IMAGE_MAX_BOX_PX to CLI_IMAGE_MAX_BOX_PX,
            sixelPixelBox(200, 100, defaultCellDisplay),
        )
    }

    @Test
    fun pixelBoxFloorsDegenerateCellCountsAtOnePixel() {
        assertEquals(1 to 180, sixelPixelBox(0, 9, defaultCellDisplay))
        assertEquals(1 to 1, sixelPixelBox(-5, -1, defaultCellDisplay))
    }

    // endregion
}
