package com.crosspaste.app

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.DesktopAppConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAppControlTest {

    private fun createControl(
        enabledSyncFileSizeLimit: Boolean = true,
        maxSyncFileSize: Long = 512,
    ): DesktopAppControl {
        val config =
            DesktopAppConfig(
                appInstanceId = "test",
                language = "en",
                enabledSyncFileSizeLimit = enabledSyncFileSizeLimit,
                maxSyncFileSize = maxSyncFileSize,
            )
        val configManager = mockk<CommonConfigManager>()
        every { configManager.getCurrentConfig() } returns config
        every { configManager.config } returns MutableStateFlow(config)
        return DesktopAppControl(configManager)
    }

    @Test
    fun `isFileSizeSyncEnabled returns true when limit disabled regardless of size`() {
        val control = createControl(enabledSyncFileSizeLimit = false)
        assertTrue(control.isFileSizeSyncEnabled(Long.MAX_VALUE))
    }

    @Test
    fun `isFileSizeSyncEnabled returns true for file within limit`() {
        // maxSyncFileSize=512 MB = 512*1024*1024 = 536870912 bytes
        val control = createControl(enabledSyncFileSizeLimit = true, maxSyncFileSize = 512)
        assertTrue(control.isFileSizeSyncEnabled(1_000_000)) // 1MB < 512MB
    }

    @Test
    fun `isFileSizeSyncEnabled returns false for file exceeding limit`() {
        // maxSyncFileSize=1 MB = 1048576 bytes
        val control = createControl(enabledSyncFileSizeLimit = true, maxSyncFileSize = 1)
        assertFalse(control.isFileSizeSyncEnabled(2_000_000)) // 2MB > 1MB
    }

    @Test
    fun `isFileSizeSyncEnabled boundary - file size equals limit returns false`() {
        // bytesSize(1) = 1048576, so 1048576 > 1048576 is false (strict greater-than)
        val control = createControl(enabledSyncFileSizeLimit = true, maxSyncFileSize = 1)
        assertFalse(control.isFileSizeSyncEnabled(1048576))
    }

    @Test
    fun `isFileSizeSyncEnabled boundary - file one byte under limit returns true`() {
        val control = createControl(enabledSyncFileSizeLimit = true, maxSyncFileSize = 1)
        assertTrue(control.isFileSizeSyncEnabled(1048575))
    }
}
