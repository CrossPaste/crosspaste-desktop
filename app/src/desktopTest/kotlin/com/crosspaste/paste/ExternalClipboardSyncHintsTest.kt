package com.crosspaste.paste

import com.crosspaste.app.AppName
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalClipboardSyncHintsTest {

    private class FakeProbe(
        private val available: Set<String>,
    ) : WindowsClipboardFormatProbe {
        var dwordReads = 0

        override fun isFormatAvailable(name: String): Boolean = name in available

        override fun readDword(name: String): Int? {
            dwordReads++
            return null
        }
    }

    @Test
    fun `windows detects RustDesk owner format without opening clipboard`() {
        val probe = FakeProbe(available = setOf(ExternalClipboardSyncHints.RUSTDESK_OWNER_FORMAT))
        assertEquals(ExternalClipboardSyncHints.RUSTDESK_SOURCE, ExternalClipboardSyncHints.sourceFromWindows(probe))
        assertEquals(0, probe.dwordReads)
    }

    @Test
    fun `windows returns null for plain clipboard formats`() {
        val probe = FakeProbe(available = setOf("CF_UNICODETEXT"))
        assertNull(ExternalClipboardSyncHints.sourceFromWindows(probe))
        assertEquals(0, probe.dwordReads)
    }

    @Test
    fun `linux detects RustDesk owner target or mime type`() {
        val targets = listOf("TARGETS", "UTF8_STRING", "text/plain", ExternalClipboardSyncHints.RUSTDESK_OWNER_FORMAT)
        assertEquals(ExternalClipboardSyncHints.RUSTDESK_SOURCE, ExternalClipboardSyncHints.sourceFromLinux(targets))
        assertNull(ExternalClipboardSyncHints.sourceFromLinux(listOf("TARGETS", "UTF8_STRING")))
        assertNull(ExternalClipboardSyncHints.sourceFromLinux(emptyList()))
    }

    @Test
    fun `macos attributes RustDesk marker to RustDesk instead of frontmost app`() =
        runTest {
            var pasteboardQueries = 0
            var frontmostQueries = 0

            val source =
                MacosPasteboardService.resolvePasteboardSource(
                    firstChange = false,
                    remoteType = MacosPasteboardService.REMOTE_RUSTDESK,
                    pasteboardSource = {
                        pasteboardQueries++
                        "OtherApp"
                    },
                    frontmostSource = {
                        frontmostQueries++
                        "Ghostty"
                    },
                )

            assertEquals(ExternalClipboardSyncHints.RUSTDESK_SOURCE, source)
            assertEquals(0, pasteboardQueries)
            assertEquals(0, frontmostQueries)
        }

    @Test
    fun `macos prefers explicit pasteboard source over frontmost app`() =
        runTest {
            var frontmostQueries = 0

            val source =
                MacosPasteboardService.resolvePasteboardSource(
                    firstChange = false,
                    remoteType = 0,
                    pasteboardSource = { "RustDesk" },
                    frontmostSource = {
                        frontmostQueries++
                        "Ghostty"
                    },
                )

            assertEquals("RustDesk", source)
            assertEquals(0, frontmostQueries)
        }

    @Test
    fun `macos falls back to frontmost app and preserves first-change CrossPaste guard`() =
        runTest {
            assertEquals(
                "Ghostty",
                MacosPasteboardService.resolvePasteboardSource(
                    firstChange = false,
                    remoteType = 0,
                    pasteboardSource = { null },
                    frontmostSource = { "Ghostty" },
                ),
            )
            assertNull(
                MacosPasteboardService.resolvePasteboardSource(
                    firstChange = true,
                    remoteType = 0,
                    pasteboardSource = { null },
                    frontmostSource = { AppName },
                ),
            )
        }

    @Test
    fun `macos keeps RustDesk remote writes local-only while preserving Apple remote filtering`() {
        var nonAppleQueries = 0
        val nonAppleTargets = {
            nonAppleQueries++
            setOf("win-peer")
        }

        assertEquals(
            emptySet(),
            MacosPasteboardService.resolveSyncTargets(MacosPasteboardService.REMOTE_RUSTDESK, nonAppleTargets),
        )
        assertEquals(0, nonAppleQueries)

        assertNull(MacosPasteboardService.resolveSyncTargets(0, nonAppleTargets))
        assertEquals(0, nonAppleQueries)

        assertEquals(
            setOf("win-peer"),
            MacosPasteboardService.resolveSyncTargets(
                MacosPasteboardService.REMOTE_UNIVERSAL_CLIPBOARD,
                nonAppleTargets,
            ),
        )
        assertEquals(
            setOf("win-peer"),
            MacosPasteboardService.resolveSyncTargets(
                MacosPasteboardService.REMOTE_XCODE_SYNC,
                nonAppleTargets,
            ),
        )
        assertEquals(2, nonAppleQueries)
    }

    @Test
    fun `macos marks only RustDesk remote writes as persisted remote items`() {
        assertTrue(MacosPasteboardService.isExternalRemoteSync(MacosPasteboardService.REMOTE_RUSTDESK))
        assertFalse(MacosPasteboardService.isExternalRemoteSync(0))
        assertFalse(MacosPasteboardService.isExternalRemoteSync(MacosPasteboardService.REMOTE_UNIVERSAL_CLIPBOARD))
        assertFalse(MacosPasteboardService.isExternalRemoteSync(MacosPasteboardService.REMOTE_XCODE_SYNC))
    }

    @Test
    fun `linux x11 overrides excluded foreground source when RustDesk target is present`() {
        val rustDeskTargets =
            listOf("TARGETS", "UTF8_STRING", ExternalClipboardSyncHints.RUSTDESK_OWNER_FORMAT)
        assertEquals(
            ExternalClipboardSyncHints.RUSTDESK_SOURCE to true,
            LinuxPasteboardService.resolveX11EffectiveSource("Bitwarden", rustDeskTargets),
        )
        assertEquals(
            "Bitwarden" to false,
            LinuxPasteboardService.resolveX11EffectiveSource("Bitwarden", listOf("TARGETS", "UTF8_STRING")),
        )
        assertEquals(
            "Ghostty" to false,
            LinuxPasteboardService.resolveX11EffectiveSource("Ghostty", null),
        )
    }
}
