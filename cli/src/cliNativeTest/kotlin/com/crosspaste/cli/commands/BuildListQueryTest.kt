package com.crosspaste.cli.commands

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildListQueryTest {

    @Test
    fun defaultsProduceOnlyTheLimit() {
        assertEquals("?limit=20", buildListQuery(limit = 20, types = listOf(), tag = null))
    }

    @Test
    fun typesRepeatForOrMatching() {
        assertEquals(
            "?limit=20&type=text&type=link",
            buildListQuery(limit = 20, types = listOf("text", "link"), tag = null),
        )
    }

    @Test
    fun queryComesFirstAndIsEncoded() {
        assertEquals(
            "?q=hello%20world&limit=5&tag=Work",
            buildListQuery(limit = 5, types = listOf(), tag = "Work", query = "hello world"),
        )
    }

    @Test
    fun sortIsForwardedWhenSet() {
        assertEquals(
            "?limit=20&sort=oldest",
            buildListQuery(limit = 20, types = listOf(), tag = null, sort = SORT_OLDEST),
        )
    }
}
