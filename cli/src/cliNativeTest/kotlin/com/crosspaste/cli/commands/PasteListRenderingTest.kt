package com.crosspaste.cli.commands

import kotlin.test.Test
import kotlin.test.assertEquals

class PasteListRenderingTest {

    @Test
    fun asciiFittingWithinTheBudgetIsUntouched() {
        assertEquals("abc", truncateToCellWidth("abc", 3))
        assertEquals("abc", truncateToCellWidth("abc", 100))
    }

    @Test
    fun asciiIsCutAtTheBudget() {
        assertEquals("abcde", truncateToCellWidth("abcdefgh", 5))
    }

    @Test
    fun cjkCountsTwoCellsPerChar() {
        // 35 cells fit 17 wide chars (34 cells), not 35 chars.
        assertEquals("汉".repeat(17), truncateToCellWidth("汉".repeat(100), 35))
    }

    @Test
    fun mixedAsciiAndCjk() {
        // "ab" = 2 cells, each 汉 = 2 cells: budget 7 fits ab + 2 wide chars.
        assertEquals("ab汉汉", truncateToCellWidth("ab汉汉汉", 7))
    }

    @Test
    fun surrogatePairEmojiIsNeverSplit() {
        // 🎉 is a surrogate pair worth 2 cells; budget 3 fits one, not one and a half.
        assertEquals("🎉", truncateToCellWidth("🎉🎉", 3))
        assertEquals("", truncateToCellWidth("🎉🎉", 1))
    }

    @Test
    fun zeroOrNegativeBudgetYieldsEmpty() {
        assertEquals("", truncateToCellWidth("abc", 0))
        assertEquals("", truncateToCellWidth("abc", -5))
    }

    @Test
    fun newlinesAndWhitespaceRunsCollapseToSingleSpaces() {
        assertEquals("a b c", collapsePreviewWhitespace("  a\n\nb \t  c  "))
    }

    @Test
    fun emojiWithVariationSelectorDoesNotUndercount() {
        // ❤️ (U+2764 U+FE0F) renders 2 cells wide; the estimate must be >= 2
        // so the row never overflows. Budget 1 must therefore keep nothing.
        assertEquals("", truncateToCellWidth("❤️x", 1))
    }
}
