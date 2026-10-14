package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.menu.PasteMenuAction
import com.crosspaste.paste.menu.PasteMenuContributor
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPasteMenuServiceTest {

    private val copywriter =
        mockk<GlobalCopywriter> {
            every { getText(any(), *anyVararg()) } answers { firstArg() }
        }

    private fun createService(contributors: List<PasteMenuContributor>): DesktopPasteMenuService =
        DesktopPasteMenuService(
            appControl = mockk(relaxed = true),
            appInfo = mockk(relaxed = true),
            appWindowManager = mockk<DesktopAppWindowManager>(relaxed = true),
            copywriter = copywriter,
            notificationManager = mockk(relaxed = true),
            pasteboardService = mockk(relaxed = true),
            pasteDao = mockk(relaxed = true),
            pasteTagDao = mockk(relaxed = true),
            pasteSearchViewModel = mockk(relaxed = true),
            syncManager = mockk(relaxed = true),
            taskSubmitter = mockk(relaxed = true),
            uiSupport = mockk(relaxed = true),
            contributors = contributors,
        )

    private fun pasteData(
        type: PasteType,
        state: Int = PasteState.LOADED,
    ): PasteData =
        mockk<PasteData>(relaxed = true) {
            every { getType() } returns type
            every { pasteState } returns state
            every { getPasteItem(PasteFiles::class) } returns null
        }

    private fun labels(
        service: DesktopPasteMenuService,
        pasteData: PasteData,
    ): List<String> = service.pasteMenuItemsProvider(pasteData)().map { it.label }

    private val imageOnlyContributor =
        PasteMenuContributor { pasteData ->
            if (pasteData.getType() == PasteType.IMAGE_TYPE) {
                listOf(PasteMenuAction("extract_text") {})
            } else {
                emptyList()
            }
        }

    @Test
    fun `contributed actions sit between primary actions and tags`() {
        val service = createService(listOf(imageOnlyContributor))

        assertEquals(
            listOf("copy", "edit", "extract_text", "tags", "sync_to", "", "delete"),
            labels(service, pasteData(PasteType.IMAGE_TYPE)),
        )
    }

    @Test
    fun `contributor returning nothing leaves the built-in menu unchanged`() {
        val service = createService(listOf(imageOnlyContributor))

        assertEquals(
            listOf("copy", "edit", "tags", "sync_to", "", "delete"),
            labels(service, pasteData(PasteType.TEXT_TYPE)),
        )
        assertEquals(
            listOf("copy", "open", "tags", "sync_to", "", "delete"),
            labels(service, pasteData(PasteType.URL_TYPE)),
        )
    }

    @Test
    fun `loading paste skips contributors`() {
        val service = createService(listOf(imageOnlyContributor))

        assertEquals(
            listOf("copy", "", "delete"),
            labels(service, pasteData(PasteType.IMAGE_TYPE, PasteState.LOADING)),
        )
    }
}
