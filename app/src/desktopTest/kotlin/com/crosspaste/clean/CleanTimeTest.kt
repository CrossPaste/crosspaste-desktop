package com.crosspaste.clean

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CleanTimeTest {

    @Test
    fun `all entries have positive days`() {
        for (entry in CleanTime.entries) {
            assertTrue(entry.days > 0, "${entry.name} should have positive days, got ${entry.days}")
        }
    }

    @Test
    fun `all entries have positive quantity`() {
        for (entry in CleanTime.entries) {
            assertTrue(entry.quantity > 0, "${entry.name} should have positive quantity")
        }
    }

    @Test
    fun `entries count is 16`() {
        assertEquals(16, CleanTime.entries.size)
    }

    @Test
    fun `ONE_DAY is 1 day`() {
        assertEquals(1, CleanTime.ONE_DAY.days)
        assertEquals(1, CleanTime.ONE_DAY.quantity)
        assertEquals("day", CleanTime.ONE_DAY.unit)
    }

    @Test
    fun `ONE_WEEK is 7 days`() {
        assertEquals(7, CleanTime.ONE_WEEK.days)
        assertEquals(1, CleanTime.ONE_WEEK.quantity)
        assertEquals("week", CleanTime.ONE_WEEK.unit)
    }

    @Test
    fun `ONE_MONTH is 30 days`() {
        assertEquals(30, CleanTime.ONE_MONTH.days)
        assertEquals(1, CleanTime.ONE_MONTH.quantity)
        assertEquals("month", CleanTime.ONE_MONTH.unit)
    }

    @Test
    fun `ONE_YEAR is 365 days`() {
        assertEquals(365, CleanTime.ONE_YEAR.days)
        assertEquals(1, CleanTime.ONE_YEAR.quantity)
        assertEquals("year", CleanTime.ONE_YEAR.unit)
    }

    @Test
    fun `HALF_YEAR is 182 days`() {
        assertEquals(182, CleanTime.HALF_YEAR.days)
        assertEquals(6, CleanTime.HALF_YEAR.quantity)
        assertEquals("month", CleanTime.HALF_YEAR.unit)
    }

    @Test
    fun `days are monotonically increasing`() {
        val entries = CleanTime.entries
        for (i in 1 until entries.size) {
            assertTrue(
                entries[i].days > entries[i - 1].days,
                "${entries[i].name}.days (${entries[i].days}) should be > ${entries[i - 1].name}.days (${entries[i - 1].days})",
            )
        }
    }

    @Test
    fun `units are valid strings`() {
        val validUnits = setOf("day", "week", "month", "year")
        for (entry in CleanTime.entries) {
            assertTrue(
                entry.unit in validUnits,
                "${entry.name} has invalid unit '${entry.unit}'",
            )
        }
    }

    @Test
    fun `ordinal indices map correctly for config usage`() {
        // CleanTime entries are used via ordinal index in AppConfig
        assertEquals(0, CleanTime.ONE_DAY.ordinal)
        assertEquals(6, CleanTime.ONE_WEEK.ordinal)
        assertEquals(9, CleanTime.ONE_MONTH.ordinal)
        assertEquals(15, CleanTime.ONE_YEAR.ordinal)
    }
}
