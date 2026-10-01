package com.crosspaste.cli.commands

import com.crosspaste.cli.CrossPasteCommand
import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DevicesCommandTest {

    private val ids =
        listOf(
            "f965b697-ed9b-4048-abff-cea3d119cb40",
            "F5F6A504-F621-11ED-C88C-08BFB81BB86E",
            "fe5998e9-368c-43fd-b9b0-a2097d343e61",
        )

    @Test
    fun exactIdWins() {
        assertEquals(DeviceIdResolution.Resolved(ids[0]), resolveDeviceId(ids[0], ids))
    }

    @Test
    fun uniquePrefixResolvesCaseInsensitively() {
        assertEquals(DeviceIdResolution.Resolved(ids[1]), resolveDeviceId("f5f6", ids))
        assertEquals(DeviceIdResolution.Resolved(ids[2]), resolveDeviceId("FE59", ids))
    }

    @Test
    fun sharedPrefixIsAmbiguous() {
        // "f" is a prefix of every id; "f9" and "f5" are not.
        assertEquals(DeviceIdResolution.Ambiguous(ids), resolveDeviceId("f", ids))
    }

    @Test
    fun unknownPrefixIsNotFound() {
        assertEquals(DeviceIdResolution.NotFound, resolveDeviceId("zzz", ids))
        assertEquals(DeviceIdResolution.NotFound, resolveDeviceId("abc", emptyList()))
    }

    @Test
    fun devicesHelpListsTheSubcommands() {
        val result = CrossPasteCommand().test("devices --help")
        assertEquals(0, result.statusCode)
        assertContains(result.stdout, "remove")
        assertContains(result.stdout, "block")
        assertContains(result.stdout, "unblock")
    }

    @Test
    fun removeRequiresAnId() {
        val result = CrossPasteCommand().test("devices remove")
        assertEquals(1, result.statusCode)
        assertContains(result.stderr, "missing argument")
    }
}
