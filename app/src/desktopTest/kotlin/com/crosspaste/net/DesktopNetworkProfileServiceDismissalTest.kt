package com.crosspaste.net

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.headless.HeadlessUserAttentionService
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DesktopLocaleUtils
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The persisted dismissal of the Windows network warning must survive a restart when
 * the next detection reports the same blocking state, and only a real detection may
 * invalidate it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopNetworkProfileServiceDismissalTest {

    private val publicBlocking = NetworkDiagnosis(NetworkProfile.PUBLIC, mDnsAllowed = false)
    private val privateBlocking = NetworkDiagnosis(NetworkProfile.PRIVATE, mDnsAllowed = false)

    private val windows = Platform(Platform.WINDOWS, "x86_64", 64, "11")

    private fun newConfigManager(storedFingerprint: String = ""): DesktopConfigManager {
        val configDir = Files.createTempDirectory("netProfileConfig").toOkioPath()
        configDir.toFile().deleteOnExit()
        val configManager =
            DesktopConfigManager(
                OneFilePersist(configDir.resolve("appConfig.json")),
                DesktopLocaleUtils,
            )
        if (storedFingerprint.isNotEmpty()) {
            configManager.updateConfig("networkBlockingDismissedFingerprint", storedFingerprint)
        }
        return configManager
    }

    private fun newService(
        configManager: DesktopConfigManager,
        scope: CoroutineScope,
        detect: () -> NetworkDiagnosis,
    ): DesktopNetworkProfileService =
        DesktopNetworkProfileService(
            configManager = configManager,
            notificationManager = mockk<NotificationManager>(relaxed = true),
            platform = windows,
            userAttentionService = HeadlessUserAttentionService(),
            scope = scope,
            detect = detect,
        )

    private fun storedFingerprint(configManager: DesktopConfigManager): String =
        configManager.getCurrentConfig().networkBlockingDismissedFingerprint

    @Test
    fun `same blocking state after restart keeps the dismissal and stays quiet`() =
        runTest {
            val configManager = newConfigManager(storedFingerprint = publicBlocking.fingerprint())
            val job = Job()
            val service = newService(configManager, CoroutineScope(coroutineContext + job)) { publicBlocking }

            // Construction alone (placeholder NOT_APPLICABLE) must not touch the dismissal.
            advanceUntilIdle()
            assertEquals(publicBlocking.fingerprint(), storedFingerprint(configManager))

            service.refresh()
            advanceUntilIdle()

            assertEquals(publicBlocking, service.diagnosis.value)
            assertEquals(publicBlocking.fingerprint(), storedFingerprint(configManager))
            assertTrue(service.isWarningDismissed.value)
            assertFalse(service.isWarningDialogVisible.value)
            job.cancel()
        }

    @Test
    fun `different blocking state clears the dismissal and surfaces the dialog`() =
        runTest {
            val configManager = newConfigManager(storedFingerprint = publicBlocking.fingerprint())
            val job = Job()
            val service = newService(configManager, CoroutineScope(coroutineContext + job)) { privateBlocking }

            service.refresh()
            advanceUntilIdle()

            assertEquals("", storedFingerprint(configManager))
            assertFalse(service.isWarningDismissed.value)
            assertTrue(service.isWarningDialogVisible.value)
            job.cancel()
        }

    @Test
    fun `failed detection does not clear the dismissal`() =
        runTest {
            val configManager = newConfigManager(storedFingerprint = publicBlocking.fingerprint())
            val job = Job()
            val service =
                newService(configManager, CoroutineScope(coroutineContext + job)) {
                    error("COM enumeration failed")
                }

            service.refresh()
            advanceUntilIdle()

            assertEquals(NetworkProfile.UNKNOWN, service.diagnosis.value.profile)
            assertEquals(publicBlocking.fingerprint(), storedFingerprint(configManager))
            assertFalse(service.isWarningDialogVisible.value)
            job.cancel()
        }

    @Test
    fun `absorbed probe failure returning UNKNOWN does not clear the dismissal`() =
        runTest {
            // WindowsNetworkApi.query() never throws; a COM failure comes back as UNKNOWN|null.
            val unknown = NetworkDiagnosis(NetworkProfile.UNKNOWN, mDnsAllowed = null)
            val configManager = newConfigManager(storedFingerprint = publicBlocking.fingerprint())
            val job = Job()
            val service = newService(configManager, CoroutineScope(coroutineContext + job)) { unknown }

            service.refresh()
            advanceUntilIdle()

            assertEquals(unknown, service.diagnosis.value)
            assertEquals(publicBlocking.fingerprint(), storedFingerprint(configManager))
            assertFalse(service.isWarningDialogVisible.value)
            job.cancel()
        }

    @Test
    fun `transient firewall read failure keeps the dismissal until the same state is read back`() =
        runTest {
            val configManager = newConfigManager(storedFingerprint = publicBlocking.fingerprint())
            var next = NetworkDiagnosis(NetworkProfile.PUBLIC, mDnsAllowed = null)
            val job = Job()
            val service = newService(configManager, CoroutineScope(coroutineContext + job)) { next }

            // Profile resolved but the firewall rules could not be enumerated.
            service.refresh()
            advanceUntilIdle()
            assertEquals(publicBlocking.fingerprint(), storedFingerprint(configManager))
            assertFalse(service.isWarningDialogVisible.value)

            // Firewall query recovers and reports the very state the user dismissed.
            next = publicBlocking
            service.refresh()
            advanceUntilIdle()
            assertEquals(publicBlocking.fingerprint(), storedFingerprint(configManager))
            assertTrue(service.isWarningDismissed.value)
            assertFalse(service.isWarningDialogVisible.value)
            job.cancel()
        }

    @Test
    fun `dismissing persists the fingerprint and repeated detections stay quiet`() =
        runTest {
            val configManager = newConfigManager()
            val job = Job()
            val service = newService(configManager, CoroutineScope(coroutineContext + job)) { publicBlocking }

            service.refresh()
            advanceUntilIdle()
            assertTrue(service.isWarningDialogVisible.value)

            service.dismissWarning()
            advanceUntilIdle()
            assertEquals(publicBlocking.fingerprint(), storedFingerprint(configManager))
            assertFalse(service.isWarningDialogVisible.value)

            // The 60s poll re-detecting the same state must not reopen the dialog.
            service.refresh()
            advanceUntilIdle()
            assertFalse(service.isWarningDialogVisible.value)
            assertTrue(service.isWarningDismissed.value)
            job.cancel()
        }
}
