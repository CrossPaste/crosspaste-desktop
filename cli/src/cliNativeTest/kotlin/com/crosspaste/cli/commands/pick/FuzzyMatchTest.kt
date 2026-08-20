package com.crosspaste.cli.commands.pick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuzzyMatchTest {

    @Test
    fun emptyQueryMatchesEverythingWithZeroScore() {
        val match = fuzzyMatch("", "anything")
        assertEquals(0, match?.score)
        assertEquals(emptyList(), match?.positions)
    }

    @Test
    fun subsequenceMatchesCaseInsensitively() {
        val match = fuzzyMatch("dkr", "Docker Compose")
        assertEquals(listOf(0, 3, 5), match?.positions)
    }

    @Test
    fun missingCharacterFailsTheMatch() {
        assertNull(fuzzyMatch("dockerx", "docker compose"))
    }

    @Test
    fun consecutiveHitsScoreHigherThanScattered() {
        val consecutive = fuzzyMatch("dock", "dock")!!
        val scattered = fuzzyMatch("dock", "d o c k")!!
        assertTrue(consecutive.score > scattered.score)
    }

    @Test
    fun earlierMatchesScoreHigherThanLaterOnes() {
        val early = fuzzyMatch("log", "log output here")!!
        val late = fuzzyMatch("log", "the program log")!!
        assertTrue(early.score > late.score)
    }

    @Test
    fun everyTermMustMatchAndPositionsMerge() {
        assertNull(fuzzyMatch("docker missing", "docker compose"))
        // "comp" matches greedily from the left: its 'c' lands on index 2
        // (already claimed by "dock"), so the sets merge
        val match = fuzzyMatch("dock comp", "docker compose")!!
        assertEquals(listOf(0, 1, 2, 3, 8, 9, 10), match.positions)
    }

    @Test
    fun cjkQueryMatchesByChar() {
        val match = fuzzyMatch("剪贴", "跨设备剪贴板同步")
        assertEquals(listOf(3, 4), match?.positions)
    }
}
