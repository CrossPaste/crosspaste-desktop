package com.crosspaste.cli.commands

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchQueryTest {

    @Test
    fun buildsEmptyQueryWithoutFilters() {
        assertEquals("", buildWatchQuery(types = listOf(), tag = null))
    }

    @Test
    fun repeatsTypeParameterForOrMatching() {
        assertEquals(
            "?type=text&type=link",
            buildWatchQuery(types = listOf("text", "link"), tag = null),
        )
    }

    @Test
    fun encodesTagValues() {
        assertEquals(
            "?tag=work%20notes",
            buildWatchQuery(types = listOf(), tag = "work notes"),
        )
    }

    @Test
    fun explicitFormatWinsOverGlobalJsonFlag() {
        assertEquals(WatchFormat.ID, resolveWatchFormat(WatchFormat.ID, json = true))
    }

    @Test
    fun globalJsonFlagImpliesJsonFormat() {
        assertEquals(WatchFormat.JSON, resolveWatchFormat(null, json = true))
    }

    @Test
    fun defaultFormatIsLine() {
        assertEquals(WatchFormat.LINE, resolveWatchFormat(null, json = false))
    }
}
