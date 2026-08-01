package com.crosspaste.net.routing

import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasteRoutingTest {

    @Test
    fun `authenticated identity replaces body identity`() {
        val pasteData = createPasteData(appInstanceId = "spoofed-peer")

        val bound = pasteData.bindAuthenticatedRemoteIdentity("authenticated-peer")

        assertEquals("authenticated-peer", bound.appInstanceId)
        assertTrue(bound.remote)
        assertEquals(pasteData.hash, bound.hash)
    }

    @Test
    fun `matching identity is still marked remote`() {
        val pasteData = createPasteData(appInstanceId = "authenticated-peer")

        val bound = pasteData.bindAuthenticatedRemoteIdentity("authenticated-peer")

        assertEquals("authenticated-peer", bound.appInstanceId)
        assertTrue(bound.remote)
    }

    private fun createPasteData(appInstanceId: String): PasteData =
        PasteData(
            appInstanceId = appInstanceId,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.TEXT_TYPE.type,
            size = 4,
            hash = "hash",
        )
}
