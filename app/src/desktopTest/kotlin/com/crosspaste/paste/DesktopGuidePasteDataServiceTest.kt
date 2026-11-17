package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppLaunchState
import com.crosspaste.db.paste.PasteDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopGuidePasteDataServiceTest {

    private class TestLaunchState(
        override val acquiredLock: Boolean = true,
        override val firstLaunch: Boolean,
    ) : AppLaunchState

    private val appInfo =
        AppInfo(
            appInstanceId = "test-instance",
            appVersion = "1.0.0",
            appRevision = "rev",
            userName = "tester",
        )

    private fun service(
        launchState: Lazy<AppLaunchState>,
        pasteDao: PasteDao = mockk(relaxed = true),
    ) = DesktopGuidePasteDataService(
        appInfo = appInfo,
        appLaunchState = launchState,
        copywriter = mockk(relaxed = true),
        pasteDao = pasteDao,
        pasteItemReader = mockk(relaxed = true),
        searchContentService = mockk(relaxed = true),
    )

    @Test
    fun `constructing the service does not resolve the launch state`() {
        val launchState = lazy<AppLaunchState> { TestLaunchState(firstLaunch = true) }

        service(launchState)

        // Mobile hosts construct this on the main thread; resolving the launch state
        // there takes the app lock (file IO).
        assertFalse(launchState.isInitialized())
    }

    @Test
    fun `initData resolves the launch state and skips seeding when not first launch`() {
        val launchState = lazy<AppLaunchState> { TestLaunchState(firstLaunch = false) }
        val pasteDao = mockk<PasteDao>(relaxed = true)

        service(launchState, pasteDao).initData()

        assertTrue(launchState.isInitialized())
        coVerify(exactly = 0) { pasteDao.getSize(any()) }
    }

    @Test
    fun `initData does not seed over existing history on first launch`() {
        val launchState = lazy<AppLaunchState> { TestLaunchState(firstLaunch = true) }
        val pasteDao = mockk<PasteDao>(relaxed = true)
        coEvery { pasteDao.getSize(any()) } returns 3L

        service(launchState, pasteDao).initData()

        coVerify(exactly = 1) { pasteDao.getSize(true) }
        coVerify(exactly = 0) { pasteDao.createPasteData(any()) }
    }
}
