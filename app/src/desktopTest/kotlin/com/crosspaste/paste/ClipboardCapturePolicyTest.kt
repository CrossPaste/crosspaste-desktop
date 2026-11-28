package com.crosspaste.paste

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.platform.Platform
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.sync.SyncHandler
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.DesktopLocaleUtils
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipboardCapturePolicyTest {

    @TempDir
    lateinit var tempDir: Path

    private class FakeProbe(
        private val available: Set<String>,
        private val dwords: Map<String, Int?> = emptyMap(),
    ) : WindowsClipboardFormatProbe {
        var dwordReads = 0

        override fun isFormatAvailable(name: String): Boolean = name in available

        override fun readDword(name: String): Int? {
            dwordReads++
            return dwords[name]
        }
    }

    private fun newExclusionService(): DesktopSourceExclusionService {
        val manager =
            DesktopConfigManager(OneFilePersist(tempDir.resolve("appConfig.json").toOkioPath()), DesktopLocaleUtils)
        return DesktopSourceExclusionService(manager)
    }

    private fun syncManagerWith(vararg peers: Pair<String, String>): SyncManager {
        val handlers =
            peers.associate { (id, platformName) ->
                id to
                    mockk<SyncHandler> {
                        every { currentSyncRuntimeInfo.platform } returns Platform(platformName, "arm64", 64, "1.0")
                    }
            }
        return mockk { every { getSyncHandlers() } returns handlers }
    }

    @Test
    fun `ignores CrossPaste self-origin markers on macOS and Linux before resolving source`() =
        runTest {
            val policy = ClipboardCapturePolicy(newExclusionService(), syncManagerWith())
            var sourceCalls = 0

            for (marker in listOf(
                ClipboardConflictRules.MACOS_CROSSPASTE_TYPE,
                PasteDataFlavors.CROSSPASTE_ORIGIN_MIME,
            )) {
                val decision =
                    policy.evaluate(
                        metadata = ClipboardMetadata(types = setOf("public.utf8-plain-text", marker)),
                        resolveExplicitSource = {
                            sourceCalls++
                            "Explicit"
                        },
                        resolveFallbackSource = {
                            sourceCalls++
                            "Fallback"
                        },
                    )
                val ignored = assertIs<ClipboardCaptureDecision.Ignore>(decision)
                assertEquals("crosspaste_origin", ignored.reason)
            }
            assertEquals(0, sourceCalls)
        }

    @Test
    fun `attributes RustDesk to RustDesk and marks it LocalOnly across types and Windows probe`() =
        runTest {
            val exclusionService = newExclusionService()
            exclusionService.addExclusion("Bitwarden")
            val syncManager =
                syncManagerWith(
                    "mac-peer" to Platform.MACOS,
                    "win-peer" to Platform.WINDOWS,
                )
            val policy = ClipboardCapturePolicy(exclusionService, syncManager)

            val probe = FakeProbe(available = setOf(ClipboardConflictRules.RUSTDESK_OWNER_FORMAT))
            val inputs =
                listOf(
                    ClipboardMetadata(
                        types = setOf("public.utf8-plain-text", ClipboardConflictRules.RUSTDESK_OWNER_FORMAT),
                    ),
                    ClipboardMetadata(formatProbe = probe),
                )

            for (metadata in inputs) {
                var fallbackCalls = 0
                val decision =
                    policy.evaluate(
                        metadata = metadata,
                        resolveFallbackSource = {
                            fallbackCalls++
                            "Bitwarden"
                        },
                    )
                val capture = assertIs<ClipboardCaptureDecision.Capture>(decision)
                assertEquals(ClipboardConflictRules.RUSTDESK_ID, capture.matchedRuleId)
                assertEquals(ClipboardConflictRules.RUSTDESK_SOURCE, capture.sourceContext.source)
                assertTrue(capture.sourceContext.remote)
                assertEquals(emptySet(), capture.sourceContext.targetAppInstanceIds)
                assertEquals(0, fallbackCalls)
            }
            assertEquals(0, probe.dwordReads)
        }

    @Test
    fun `honors Source Exclusion and pattern rules for background RustDesk writes`() =
        runTest {
            val exclusionService = newExclusionService()
            exclusionService.addExclusion("RustDesk")
            val policy = ClipboardCapturePolicy(exclusionService, syncManagerWith())

            val exact =
                policy.evaluate(
                    metadata = ClipboardMetadata(types = setOf(ClipboardConflictRules.RUSTDESK_OWNER_FORMAT)),
                    resolveFallbackSource = { "Ghostty" },
                )
            assertIs<ClipboardCaptureDecision.Ignore>(exact)

            exclusionService.removeExclusion("RustDesk")
            exclusionService.addPattern("rust")
            val pattern =
                policy.evaluate(
                    metadata =
                        ClipboardMetadata(
                            formatProbe = FakeProbe(setOf(ClipboardConflictRules.RUSTDESK_OWNER_FORMAT)),
                        ),
                    resolveFallbackSource = { "Notepad" },
                )
            assertIs<ClipboardCaptureDecision.Ignore>(pattern)
        }

    @Test
    fun `Apple Universal Clipboard notifies and skips Apple peers while Xcode sync only skips`() =
        runTest {
            var universalNotifications = 0
            val syncManager =
                syncManagerWith(
                    "iphone" to Platform.IPHONE,
                    "macbook" to Platform.MACOS,
                    "win" to Platform.WINDOWS,
                    "linux" to Platform.LINUX,
                )
            val policy =
                ClipboardCapturePolicy(
                    sourceExclusionService = newExclusionService(),
                    syncManager = syncManager,
                    conflictNotifiers =
                        mapOf(
                            ClipboardConflictRules.APPLE_UNIVERSAL_CLIPBOARD_ID to { universalNotifications++ },
                        ),
                )

            val universal =
                assertIs<ClipboardCaptureDecision.Capture>(
                    policy.evaluate(
                        metadata =
                            ClipboardMetadata(
                                types = setOf(ClipboardConflictRules.APPLE_UNIVERSAL_CLIPBOARD_TYPE),
                            ),
                        resolveFallbackSource = { "Notes" },
                    ),
                )
            assertEquals("Notes", universal.sourceContext.source)
            assertEquals(false, universal.sourceContext.remote)
            assertEquals(setOf("win", "linux"), universal.sourceContext.targetAppInstanceIds)
            assertEquals(1, universalNotifications)

            val xcode =
                assertIs<ClipboardCaptureDecision.Capture>(
                    policy.evaluate(
                        metadata = ClipboardMetadata(types = setOf(ClipboardConflictRules.APPLE_XCODE_SYNC_TYPE)),
                        resolveFallbackSource = { "Xcode" },
                    ),
                )
            assertEquals(false, xcode.sourceContext.remote)
            assertEquals(setOf("win", "linux"), xcode.sourceContext.targetAppInstanceIds)
            assertEquals(1, universalNotifications)
        }

    @Test
    fun `does not invoke conflict notifier when content is concealed or excluded`() =
        runTest {
            var notifications = 0
            val exclusionService = newExclusionService()
            exclusionService.addExclusion("Notes")
            val policy =
                ClipboardCapturePolicy(
                    sourceExclusionService = exclusionService,
                    syncManager = syncManagerWith("mac" to Platform.MACOS),
                    conflictNotifiers =
                        mapOf(
                            ClipboardConflictRules.APPLE_UNIVERSAL_CLIPBOARD_ID to { notifications++ },
                        ),
                )

            val excluded =
                policy.evaluate(
                    metadata = ClipboardMetadata(types = setOf(ClipboardConflictRules.APPLE_UNIVERSAL_CLIPBOARD_TYPE)),
                    resolveFallbackSource = { "Notes" },
                )
            assertIs<ClipboardCaptureDecision.Ignore>(excluded)

            val concealed =
                policy.evaluate(
                    metadata =
                        ClipboardMetadata(
                            types =
                                setOf(
                                    ClipboardConflictRules.APPLE_UNIVERSAL_CLIPBOARD_TYPE,
                                    "org.nspasteboard.ConcealedType",
                                ),
                        ),
                    resolveFallbackSource = { "Safari" },
                )
            assertIs<ClipboardCaptureDecision.Ignore>(concealed)
            assertEquals(0, notifications)
        }

    @Test
    fun `prefers explicit pasteboard source over fallback and avoids Windows DWORD read for excluded source`() =
        runTest {
            val exclusionService = newExclusionService()
            exclusionService.addExclusion("Bitwarden")
            val policy = ClipboardCapturePolicy(exclusionService, syncManagerWith())

            var fallbackCalls = 0
            val explicit =
                assertIs<ClipboardCaptureDecision.Capture>(
                    policy.evaluate(
                        metadata = ClipboardMetadata(types = setOf("public.utf8-plain-text")),
                        resolveExplicitSource = { "Calculator" },
                        resolveFallbackSource = {
                            fallbackCalls++
                            "Ghostty"
                        },
                    ),
                )
            assertEquals("Calculator", explicit.sourceContext.source)
            assertEquals(false, explicit.sourceContext.remote)
            assertNull(explicit.sourceContext.targetAppInstanceIds)
            assertEquals(0, fallbackCalls)

            val probe =
                FakeProbe(
                    available = setOf(PasswordManagerHints.WINDOWS_CAN_INCLUDE_IN_HISTORY),
                    dwords = mapOf(PasswordManagerHints.WINDOWS_CAN_INCLUDE_IN_HISTORY to 0),
                )
            val excluded =
                policy.evaluate(
                    metadata = ClipboardMetadata(formatProbe = probe),
                    resolveFallbackSource = { "Bitwarden" },
                )
            assertIs<ClipboardCaptureDecision.Ignore>(excluded)
            assertEquals(0, probe.dwordReads)
        }

    @Test
    fun `supports registering custom conflict rules without modifying policy or platform services`() =
        runTest {
            var customNotified = 0
            val customRule =
                FormatConflictRule(
                    id = "deskflow",
                    markerFormats = setOf("com.deskflow.owner"),
                    action =
                        ClipboardConflictAction.Capture(
                            sourceOverride = "Deskflow",
                            syncPolicy = ClipboardSyncPolicy.LocalOnly,
                        ),
                )
            val policy =
                ClipboardCapturePolicy(
                    sourceExclusionService = newExclusionService(),
                    syncManager = syncManagerWith("win" to Platform.WINDOWS),
                    conflictRules = ClipboardConflictRules.defaultRules + customRule,
                    conflictNotifiers = mapOf("deskflow" to { customNotified++ }),
                )

            val capture =
                assertIs<ClipboardCaptureDecision.Capture>(
                    policy.evaluate(
                        metadata = ClipboardMetadata(types = setOf("com.deskflow.owner")),
                        resolveFallbackSource = { "Terminal" },
                    ),
                )
            assertEquals("deskflow", capture.matchedRuleId)
            assertEquals("Deskflow", capture.sourceContext.source)
            assertTrue(capture.sourceContext.remote)
            assertEquals(emptySet(), capture.sourceContext.targetAppInstanceIds)
            assertEquals(1, customNotified)
        }
}
