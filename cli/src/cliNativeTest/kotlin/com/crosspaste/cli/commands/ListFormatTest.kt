package com.crosspaste.cli.commands

import kotlin.test.Test
import kotlin.test.assertEquals

class ListFormatTest {

    @Test
    fun explicitFormatWinsOverTheGlobalJsonFlag() {
        assertEquals(ListFormat.TABLE, resolveListFormat(ListFormat.TABLE, json = true))
        assertEquals(ListFormat.ID, resolveListFormat(ListFormat.ID, json = true))
    }

    @Test
    fun globalJsonFlagImpliesJsonFormat() {
        assertEquals(ListFormat.JSON, resolveListFormat(null, json = true))
    }

    @Test
    fun defaultIsTable() {
        assertEquals(ListFormat.TABLE, resolveListFormat(null, json = false))
    }
}
