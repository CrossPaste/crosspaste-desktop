package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.plugin.type.PasteTypePlugin
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasteCollectorTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val appInfo =
        AppInfo(
            appInstanceId = "test-instance",
            appVersion = "1.0.0",
            appRevision = "abc",
            userName = "testUser",
        )
    private val pasteDao: PasteDao = mockk(relaxed = true)

    private interface TestPasteTypePlugin : PasteTypePlugin

    // ========== needPreCollectionItem ==========

    @Test
    fun `needPreCollectionItem returns true when not collected yet`() {
        val collector = PasteCollector(1, appInfo, pasteDao)
        assertTrue(collector.needPreCollectionItem(0, TestPasteTypePlugin::class))
    }

    @Test
    fun `needPreCollectionItem returns false after preCollectItem`() {
        val collector = PasteCollector(1, appInfo, pasteDao)
        val item = createTextPasteItem(text = "test")
        collector.preCollectItem(0, TestPasteTypePlugin::class, item)
        assertFalse(collector.needPreCollectionItem(0, TestPasteTypePlugin::class))
    }

    // ========== needUpdateCollectItem ==========

    @Test
    fun `needUpdateCollectItem returns true when not updated yet`() {
        val collector = PasteCollector(1, appInfo, pasteDao)
        assertTrue(collector.needUpdateCollectItem(0, TestPasteTypePlugin::class))
    }

    @Test
    fun `needUpdateCollectItem returns false after updateCollectItem`() {
        val collector = PasteCollector(1, appInfo, pasteDao)
        val item = createTextPasteItem(text = "test")
        collector.preCollectItem(0, TestPasteTypePlugin::class, item)
        collector.updateCollectItem(0, TestPasteTypePlugin::class) { it }
        assertFalse(collector.needUpdateCollectItem(0, TestPasteTypePlugin::class))
    }

    // ========== preCollectItem ==========

    @Test
    fun `preCollectItem stores item at correct index`() {
        val collector = PasteCollector(3, appInfo, pasteDao)
        val item = createTextPasteItem(text = "item2")
        collector.preCollectItem(1, TestPasteTypePlugin::class, item)

        // Index 0 and 2 should still need collection
        assertTrue(collector.needPreCollectionItem(0, TestPasteTypePlugin::class))
        assertFalse(collector.needPreCollectionItem(1, TestPasteTypePlugin::class))
        assertTrue(collector.needPreCollectionItem(2, TestPasteTypePlugin::class))
    }

    // ========== updateCollectItem ==========

    @Test
    fun `updateCollectItem applies transform to pre-collected item`() {
        val collector = PasteCollector(1, appInfo, pasteDao)
        val item = createTextPasteItem(text = "original")
        collector.preCollectItem(0, TestPasteTypePlugin::class, item)

        val replacement = createTextPasteItem(text = "updated")
        collector.updateCollectItem(0, TestPasteTypePlugin::class) { replacement }

        // After update, needUpdateCollectItem should be false
        assertFalse(collector.needUpdateCollectItem(0, TestPasteTypePlugin::class))
    }

    @Test
    fun `updateCollectItem without pre-collect does nothing`() {
        val collector = PasteCollector(1, appInfo, pasteDao)
        // Try to update without pre-collecting - should not crash
        collector.updateCollectItem(0, TestPasteTypePlugin::class) { it }
        // needUpdateCollectItem should still be true since no pre-collect happened
        assertTrue(collector.needUpdateCollectItem(0, TestPasteTypePlugin::class))
    }

    // ========== collectError ==========

    @Test
    fun `collectError records error`() {
        val collector = PasteCollector(1, appInfo, pasteDao)
        collector.collectError(1L, 0, RuntimeException("test error"))
        // Error recorded, but we can't directly assert existError
        // We verify indirectly through completeCollect behavior
    }

    // ========== createPrePasteData ==========

    @Test
    fun `createPrePasteData with no items returns null`() =
        runTest {
            val collector = PasteCollector(1, appInfo, pasteDao)
            val result = collector.createPrePasteData(null, false)
            assertNull(result)
        }

    @Test
    fun `createPrePasteData with items calls pasteDao createPasteData`() =
        runTest {
            val collector = PasteCollector(1, appInfo, pasteDao)
            val item = createTextPasteItem(text = "test")
            collector.preCollectItem(0, TestPasteTypePlugin::class, item)

            coEvery { pasteDao.createPasteData(any()) } returns 42L

            val result = collector.createPrePasteData("source", false)

            assertEquals(42L, result)
            coVerify { pasteDao.createPasteData(any()) }
        }

    // ========== completeCollect ==========

    @Test
    fun `completeCollect with no items marks paste for deletion`() =
        runTest {
            val collector = PasteCollector(1, appInfo, pasteDao)
            collector.completeCollect(1L)
            coVerify { pasteDao.markDeletePasteData(1L) }
        }

    @Test
    fun `completeCollect with items calls releaseLocalPasteData`() =
        runTest {
            val collector = PasteCollector(1, appInfo, pasteDao)
            val item = createTextPasteItem(text = "test")
            collector.preCollectItem(0, TestPasteTypePlugin::class, item)

            collector.completeCollect(1L)

            coVerify { pasteDao.releaseLocalPasteData(1L, any()) }
        }

    @Test
    fun `completeCollect with all indices errored marks paste for deletion`() =
        runTest {
            val collector = PasteCollector(1, appInfo, pasteDao)
            val item = createTextPasteItem(text = "test")
            collector.preCollectItem(0, TestPasteTypePlugin::class, item)
            collector.collectError(1L, 0, RuntimeException("error"))

            collector.completeCollect(1L)

            coVerify { pasteDao.markDeletePasteData(1L) }
        }

    @Test
    fun `completeCollect with partial errors releases remaining items`() =
        runTest {
            val collector = PasteCollector(2, appInfo, pasteDao)
            val item0 = createTextPasteItem(text = "test0")
            val item1 = createTextPasteItem(text = "test1")
            collector.preCollectItem(0, TestPasteTypePlugin::class, item0)
            collector.preCollectItem(1, TestPasteTypePlugin::class, item1)

            // Only index 0 has an error
            collector.collectError(1L, 0, RuntimeException("error"))

            collector.completeCollect(1L)

            // Should still release since not all active indices errored
            coVerify { pasteDao.releaseLocalPasteData(1L, any()) }
        }

    @Test
    fun `completeCollect with exception marks paste for deletion`() =
        runTest {
            val collector = PasteCollector(1, appInfo, pasteDao)
            val item = createTextPasteItem(text = "test")
            collector.preCollectItem(0, TestPasteTypePlugin::class, item)

            coEvery { pasteDao.releaseLocalPasteData(any(), any()) } throws RuntimeException("release error")

            collector.completeCollect(1L)

            coVerify { pasteDao.markDeletePasteData(1L) }
        }
}
