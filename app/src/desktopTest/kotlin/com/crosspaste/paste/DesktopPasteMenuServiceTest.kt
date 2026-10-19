package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.menu.PasteMenuAction
import com.crosspaste.paste.menu.PasteMenuContributor
import com.crosspaste.ui.contextmenu.ContextMenuGroup
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        pasteFiles: PasteFiles? = null,
    ): PasteData =
        mockk<PasteData>(relaxed = true) {
            every { getType() } returns type
            every { pasteState } returns state
            every { getPasteItem(PasteFiles::class) } returns pasteFiles
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

    @Test
    fun `file type paste includes open and excludes edit`() {
        val service = createService(emptyList())

        assertEquals(
            listOf("copy", "open", "tags", "sync_to", "", "delete"),
            labels(service, pasteData(PasteType.FILE_TYPE)),
        )
    }

    @Test
    fun `owned files add cut action for file and image types`() {
        val service = createService(listOf(imageOnlyContributor))
        val ownedFiles = mockk<PasteFiles> { every { isRefFiles() } returns false }
        val refFiles = mockk<PasteFiles> { every { isRefFiles() } returns true }

        assertEquals(
            listOf("copy", "cut", "open", "tags", "sync_to", "", "delete"),
            labels(service, pasteData(PasteType.FILE_TYPE, pasteFiles = ownedFiles)),
        )
        assertEquals(
            listOf("copy", "open", "tags", "sync_to", "", "delete"),
            labels(service, pasteData(PasteType.FILE_TYPE, pasteFiles = refFiles)),
        )
        assertEquals(
            listOf("copy", "cut", "edit", "extract_text", "tags", "sync_to", "", "delete"),
            labels(service, pasteData(PasteType.IMAGE_TYPE, pasteFiles = ownedFiles)),
        )
    }

    @Test
    fun `multiple contributors are evaluated in order`() {
        val contributor1 = PasteMenuContributor { listOf(PasteMenuAction("action_1") {}) }
        val contributor2 = PasteMenuContributor { listOf(PasteMenuAction("action_2") {}) }
        val service = createService(listOf(contributor1, contributor2))

        assertEquals(
            listOf("copy", "edit", "action_1", "action_2", "tags", "sync_to", "", "delete"),
            labels(service, pasteData(PasteType.TEXT_TYPE)),
        )
    }

    @Test
    fun `contributed action with children becomes a submenu`() {
        val contributor =
            PasteMenuContributor {
                listOf(
                    PasteMenuAction(
                        label = "translate",
                        children = listOf(PasteMenuAction("english") {}, PasteMenuAction("chinese") {}),
                    ),
                )
            }
        val service = createService(listOf(contributor))

        val items = service.pasteMenuItemsProvider(pasteData(PasteType.TEXT_TYPE))()
        assertEquals(listOf("copy", "edit", "translate", "tags", "sync_to", "", "delete"), items.map { it.label })
        val group = assertIs<ContextMenuGroup>(items[2])
        assertEquals(listOf("english", "chinese"), group.items().map { it.label })
    }

    @Test
    fun `faulty contributor does not break remaining menu items`() {
        val brokenContributor = PasteMenuContributor { error("Boom!") }
        val workingContributor = PasteMenuContributor { listOf(PasteMenuAction("safe_action") {}) }
        val service = createService(listOf(brokenContributor, workingContributor))

        assertEquals(
            listOf("copy", "edit", "safe_action", "tags", "sync_to", "", "delete"),
            labels(service, pasteData(PasteType.TEXT_TYPE)),
        )
    }
}
