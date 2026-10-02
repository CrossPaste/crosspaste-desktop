package com.crosspaste.app

import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.config.DesktopConfigManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class DesktopAppUpdateServiceTest {

    private fun service(
        current: String,
        latest: String?,
        autoDownload: Boolean,
        channel: WindowsUpdateChannel,
        updater: WindowsZipUpdater,
    ): DesktopAppUpdateService {
        val appInfo = mockk<AppInfo>(relaxed = true) { every { appVersion } returns current }
        val config = DesktopAppConfig(language = "en", autoDownloadUpdate = autoDownload)
        val configManager = mockk<DesktopConfigManager> { every { getCurrentConfig() } returns config }
        val fetcher =
            mockk<UpdateMetadataFetcher> {
                coEvery { fetchLatest(any(), any()) } returns
                    latest?.let { ReleaseMetadata(it, "1", "$it.1") }
            }
        every { updater.channel } returns channel
        every { updater.overrideMetadataUrl } returns null
        return DesktopAppUpdateService(
            appInfo = appInfo,
            appUrls = mockk(relaxed = true),
            configManager = configManager,
            uiSupport = mockk(relaxed = true),
            notificationManager = mockk(relaxed = true),
            metadataFetcher = fetcher,
            windowsZipUpdater = updater,
            appWindowManager = mockk(relaxed = true),
        )
    }

    @Test
    fun `a newer release starts a background download on the portable zip channel`() {
        val updater = mockk<WindowsZipUpdater>(relaxed = true)
        val service = service("1.0.0", "1.1.0", autoDownload = true, WindowsUpdateChannel.PORTABLE_ZIP, updater)

        runBlocking { service.checkForUpdate() }

        verify(exactly = 1) { updater.startBackgroundDownload() }
    }

    @Test
    fun `no download when the release is not newer`() {
        val updater = mockk<WindowsZipUpdater>(relaxed = true)
        val service = service("1.1.0", "1.1.0", autoDownload = true, WindowsUpdateChannel.PORTABLE_ZIP, updater)

        runBlocking { service.checkForUpdate() }

        verify(exactly = 0) { updater.startBackgroundDownload() }
    }

    @Test
    fun `no download when automatic downloads are off`() {
        val updater = mockk<WindowsZipUpdater>(relaxed = true)
        val service = service("1.0.0", "1.1.0", autoDownload = false, WindowsUpdateChannel.PORTABLE_ZIP, updater)

        runBlocking { service.checkForUpdate() }

        verify(exactly = 0) { updater.startBackgroundDownload() }
    }

    @Test
    fun `a manual check starts a visible download when automatic downloads are on`() {
        val updater = mockk<WindowsZipUpdater>(relaxed = true)
        val service = service("1.0.0", "1.1.0", autoDownload = true, WindowsUpdateChannel.PORTABLE_ZIP, updater)
        runBlocking { service.checkForUpdate() }

        service.tryTriggerUpdate()

        verify(exactly = 1) { updater.startDownload() }
    }

    @Test
    fun `a manual check only re-arms the prompt when automatic downloads are off`() {
        val updater = mockk<WindowsZipUpdater>(relaxed = true)
        val service = service("1.0.0", "1.1.0", autoDownload = false, WindowsUpdateChannel.PORTABLE_ZIP, updater)
        runBlocking { service.checkForUpdate() }

        service.tryTriggerUpdate()

        verify(exactly = 0) { updater.startDownload() }
        verify(exactly = 1) { updater.resetUpdatePrompt() }
    }

    @Test
    fun `no download on channels the OS updates`() {
        val updater = mockk<WindowsZipUpdater>(relaxed = true)
        val service = service("1.0.0", "1.1.0", autoDownload = true, WindowsUpdateChannel.CONVEYOR_INSTALLER, updater)

        runBlocking { service.checkForUpdate() }

        verify(exactly = 0) { updater.startBackgroundDownload() }
    }

    @Test
    fun `a manual check triggers update when already ready to apply even if lastVersion is null`() {
        val updater = mockk<WindowsZipUpdater>(relaxed = true)
        every { updater.updateState } returns MutableStateFlow(UpdateState.ReadyToApply("1.1.0"))
        val service = service("1.0.0", null, autoDownload = true, WindowsUpdateChannel.PORTABLE_ZIP, updater)

        service.tryTriggerUpdate()

        verify(exactly = 1) { updater.resetUpdatePrompt() }
        verify(exactly = 0) { updater.startDownload() }
    }
}
