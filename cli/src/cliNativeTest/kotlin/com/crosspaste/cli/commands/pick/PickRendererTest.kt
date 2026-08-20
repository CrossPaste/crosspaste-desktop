package com.crosspaste.cli.commands.pick

import com.crosspaste.cli.commands.PasteSummaryDto
import com.crosspaste.cli.commands.collapsePreviewWhitespace
import com.github.ajalt.mordant.rendering.AnsiLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun row(
    id: Long,
    preview: String,
    typeName: String = "text",
): PickRow {
    val displayText = collapsePreviewWhitespace(preview)
    return PickRow(
        item =
            PasteSummaryDto(
                id = id,
                typeName = typeName,
                source = null,
                size = preview.length.toLong(),
                tagged = false,
                createTime = 0L,
                preview = preview,
                remote = false,
            ),
        displayText = displayText,
        match = fuzzyMatch("", displayText)!!,
    )
}

private fun frameData(
    rows: List<PickRow>,
    selected: Int = 0,
    query: String = "",
    helpOpen: Boolean = false,
    previewOpen: Boolean = false,
    previewLines: List<String> = emptyList(),
    previewLink: String? = null,
    filterBar: FilterBarKind? = null,
    tagNames: List<String> = emptyList(),
) = PickFrameData(
    query = query,
    rows = rows,
    selected = selected,
    total = rows.size.toLong(),
    filters = PickFilters(types = emptyList(), tag = null),
    searching = false,
    spinnerFrame = 0,
    previewOpen = previewOpen,
    helpOpen = helpOpen,
    filterBar = filterBar,
    tagNames = tagNames,
    previewLines = previewLines,
    previewLink = previewLink,
)

class PickRendererTest {

    @Test
    fun frameHasExactlyTheRequestedHeight() {
        val frame = renderPickFrame(frameData(listOf(row(1, "one"))), width = 80, height = 24, level = AnsiLevel.NONE)
        assertEquals(24, frame.lines.size)
    }

    @Test
    fun plainRenderingContainsRowsAndMarksTheSelection() {
        val frame =
            renderPickFrame(
                frameData(listOf(row(1, "first row"), row(2, "second row")), selected = 1),
                width = 80,
                height = 24,
                level = AnsiLevel.NONE,
            )
        val body = frame.lines.joinToString("\n")
        assertTrue("first row" in body)
        assertTrue("second row" in body)
        val selectedLine = frame.lines.first { "second row" in it }
        assertTrue(selectedLine.startsWith("▌"))
        val unselectedLine = frame.lines.first { "first row" in it }
        assertTrue(unselectedLine.startsWith(" "))
    }

    @Test
    fun cursorSitsAfterTheQueryInDisplayCells() {
        val ascii = renderPickFrame(frameData(emptyList(), query = "ab"), 80, 24, AnsiLevel.NONE)
        assertEquals(2, ascii.cursorRow)
        assertEquals(5, ascii.cursorCol)
        // CJK chars occupy two cells each
        val cjk = renderPickFrame(frameData(emptyList(), query = "剪贴"), 80, 24, AnsiLevel.NONE)
        assertEquals(7, cjk.cursorCol)
    }

    @Test
    fun helpOverlayReplacesTheList() {
        val frame =
            renderPickFrame(
                frameData(listOf(row(1, "hidden row")), helpOpen = true),
                width = 80,
                height = 24,
                level = AnsiLevel.NONE,
            )
        val body = frame.lines.joinToString("\n")
        assertTrue("hidden row" !in body)
        assertTrue("ctrl-e" in body)
    }

    @Test
    fun previewPanelShowsLinesAndHyperlinksTheFirstOne() {
        val frame =
            renderPickFrame(
                frameData(
                    listOf(row(1, "https://example.com", typeName = "link")),
                    previewOpen = true,
                    previewLines = listOf("https://example.com"),
                    previewLink = "https://example.com",
                ),
                width = 80,
                height = 24,
                level = AnsiLevel.NONE,
            )
        val body = frame.lines.joinToString("\n")
        assertTrue("─ preview " in body)
        assertTrue("]8;;https://example.com" in body)
    }

    @Test
    fun longPreviewsTruncateToTheTerminalWidth() {
        val wide = "x".repeat(500)
        val frame =
            renderPickFrame(frameData(listOf(row(1, wide))), width = 60, height = 10, level = AnsiLevel.NONE)
        frame.lines.forEach { line ->
            assertTrue(line.length <= 60, "line overflows: ${line.length} cells")
        }
    }

    @Test
    fun emptyListShowsAHintInsteadOfRows() {
        val noQuery = renderPickFrame(frameData(emptyList()), 80, 24, AnsiLevel.NONE)
        assertTrue(noQuery.lines.any { "No pastes." in it })
        val withQuery = renderPickFrame(frameData(emptyList(), query = "zzz"), 80, 24, AnsiLevel.NONE)
        assertTrue(withQuery.lines.any { "No matches" in it })
    }

    @Test
    fun scrollOffsetKeepsTheSelectionVisible() {
        assertEquals(0, scrollOffset(selected = 0, rowCount = 100, listHeight = 10))
        assertEquals(0, scrollOffset(selected = 3, rowCount = 8, listHeight = 10))
        assertEquals(90, scrollOffset(selected = 99, rowCount = 100, listHeight = 10))
        val mid = scrollOffset(selected = 50, rowCount = 100, listHeight = 10)
        assertTrue(50 in mid until mid + 10)
    }

    @Test
    fun colorSwatchParsesHexPreviews() {
        val palette = StylePalette(AnsiLevel.NONE)
        assertTrue(colorSwatch("#1E80FF", palette) != null)
        assertTrue(colorSwatch("not a color", palette) == null)
    }

    @Test
    fun pasteboardControlCharsNeverReachTheFrame() {
        val esc = 27.toChar()
        val bel = 7.toChar()
        val csiC1 = 0x90.toChar()
        val malicious = row(1, "evil$esc[?1049l${bel}payload${csiC1}more")
        val frame =
            renderPickFrame(
                frameData(
                    listOf(malicious),
                    previewOpen = true,
                    previewLines = listOf("detail$esc]0;fake$bel"),
                ),
                width = 80,
                height = 24,
                level = AnsiLevel.NONE,
            )
        val body = frame.lines.joinToString("\n")
        assertTrue(esc !in body)
        assertTrue(bel !in body)
        assertTrue(csiC1 !in body)
        assertTrue("evil" in body && "payload" in body)
    }

    @Test
    fun hyperlinkRefusesUrlsWithControlChars() {
        val esc = 27.toChar()
        assertEquals("text", hyperlink("https://x$esc]0;fake", "text"))
        assertEquals("text", hyperlink("https://x${7.toChar()}", "text"))
        assertTrue(esc in hyperlink("https://example.com", "text"))
    }

    @Test
    fun longQueriesShowTheirTailAndKeepTheCursorInsideTheFrame() {
        val longQuery = "q".repeat(200) + "tail"
        val frame = renderPickFrame(frameData(emptyList(), query = longQuery), 40, 24, AnsiLevel.NONE)
        assertTrue(frame.cursorCol <= 40)
        assertTrue(frame.lines[1].endsWith("tail"))
    }

    @Test
    fun tailTruncationKeepsSuffixesAndSurrogatePairs() {
        assertEquals("def", tailToCellWidth("abcdef", 3))
        // CJK chars are two cells each
        assertEquals("贴板", tailToCellWidth("剪贴板", 4))
        assertEquals("abc", tailToCellWidth("🎉abc", 3))
    }

    @Test
    fun compactLayoutDropsTimeAndSizeOnNarrowTerminals() {
        val frame =
            renderPickFrame(
                frameData(listOf(row(1, "narrow terminal row"), row(2, "second")), selected = 1),
                width = 50,
                height = 12,
                level = AnsiLevel.NONE,
            )
        val body = frame.lines.joinToString("\n")
        assertTrue("ago" !in body)
        assertTrue("narrow terminal row" in body)
        frame.lines.forEach { assertTrue(it.length <= 50, "line overflows: ${it.length}") }
    }

    @Test
    fun highlightNeverSplitsASurrogatePair() {
        val displayText = "x🎉y"
        val match = fuzzyMatch("🎉", displayText)!!
        val palette = StylePalette(AnsiLevel.TRUECOLOR)
        val highlighted = highlightMatches(displayText, match.positions.toSet(), palette)
        // The pair must stay adjacent — no ANSI codes between its two chars
        assertTrue("🎉" in highlighted)
    }

    @Test
    fun filterBarListsCandidatesAndHandlesTheEmptyTagCase() {
        val typeBar =
            renderPickFrame(
                frameData(listOf(row(1, "x")), filterBar = FilterBarKind.TYPE),
                80,
                24,
                AnsiLevel.NONE,
            ).lines.joinToString("\n")
        assertTrue("type ▸" in typeBar)
        assertTrue(" all " in typeBar && " text " in typeBar)
        val tagBar =
            renderPickFrame(
                frameData(listOf(row(1, "x")), filterBar = FilterBarKind.TAG, tagNames = listOf("Work")),
                80,
                24,
                AnsiLevel.NONE,
            ).lines.joinToString("\n")
        assertTrue("(none)" in tagBar && " Work " in tagBar)
        val emptyTags =
            renderPickFrame(
                frameData(listOf(row(1, "x")), filterBar = FilterBarKind.TAG),
                80,
                24,
                AnsiLevel.NONE,
            ).lines.joinToString("\n")
        assertTrue("no tags yet" in emptyTags)
    }

    @Test
    fun listHeightIsTheSingleSourceOfTruthForTheLayout() {
        // The frame must devote exactly pickListHeight rows to the list under
        // every layout combination — the event loop's page size relies on it
        val rows = (1..50).map { row(it.toLong(), "row $it") }
        for (height in listOf(8, 12, 24, 40)) {
            for (previewOpen in listOf(false, true)) {
                for (barOpen in listOf(false, true)) {
                    val frame =
                        renderPickFrame(
                            frameData(
                                rows,
                                previewOpen = previewOpen,
                                previewLines = listOf("x"),
                                filterBar = if (barOpen) FilterBarKind.TYPE else null,
                            ),
                            width = 80,
                            height = height,
                            level = AnsiLevel.NONE,
                        )
                    val expectedList = pickListHeight(height, previewOpen, helpOpen = false, filterBarOpen = barOpen)
                    val listRows = frame.lines.count { it.startsWith(" ") && "row " in it || it.startsWith("▌") }
                    assertEquals(height, frame.lines.size, "total height=$height preview=$previewOpen bar=$barOpen")
                    assertEquals(
                        expectedList,
                        listRows,
                        "list rows at height=$height preview=$previewOpen bar=$barOpen",
                    )
                }
            }
        }
    }

    @Test
    fun shortTerminalsDropThePreviewPanelInsteadOfTheFooter() {
        val frame =
            renderPickFrame(
                frameData(listOf(row(1, "x")), previewOpen = true, previewLines = listOf("content")),
                width = 80,
                height = 10,
                level = AnsiLevel.NONE,
            )
        val body = frame.lines.joinToString("\n")
        assertTrue("─ preview " !in body)
        assertTrue("esc quit" in body)
        assertEquals(10, frame.lines.size)
    }

    @Test
    fun truecolorFramesCarryAnsiCodesAndPlainFramesDoNot() {
        val data = frameData(listOf(row(1, "styled")), query = "sty")
        val plain = renderPickFrame(data, 80, 24, AnsiLevel.NONE).lines.joinToString("\n")
        assertTrue(27.toChar() !in plain)
        val truecolor = renderPickFrame(data, 80, 24, AnsiLevel.TRUECOLOR).lines.joinToString("\n")
        assertTrue(27.toChar() in truecolor)
    }
}
