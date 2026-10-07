package com.crosspaste.paste

import com.crosspaste.paste.MacosPasteboardService.Companion.readUnchangedSnapshot
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MacosPasteboardSnapshotTest {

    private val logger = KotlinLogging.logger {}

    @Test
    fun `keeps the snapshot when the change count held during the read`() =
        runTest {
            val snapshot = readUnchangedSnapshot(7, { 7 }, logger) { "contents" }

            assertEquals("contents", snapshot)
        }

    @Test
    fun `discards the snapshot when another write landed during the read`() =
        runTest {
            var changeCount = 7
            val snapshot =
                readUnchangedSnapshot(7, { changeCount }, logger) {
                    changeCount = 8 // another app writes while AWT reads type by type
                    "mixed contents"
                }

            assertNull(snapshot)
        }

    @Test
    fun `does not re-check the change count when nothing was read`() =
        runTest {
            var checks = 0
            val snapshot =
                readUnchangedSnapshot<String>(7, {
                    checks++
                    8
                }, logger) { null }

            assertNull(snapshot)
            assertEquals(0, checks)
        }
}
