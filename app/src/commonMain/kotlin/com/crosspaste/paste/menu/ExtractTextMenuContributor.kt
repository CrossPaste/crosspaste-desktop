package com.crosspaste.paste.menu

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppName
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.OCRModule
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.path.UserDataPathProvider

class ExtractTextMenuContributor(
    private val appInfo: AppInfo,
    private val copywriter: GlobalCopywriter,
    private val ocrModule: OCRModule,
    private val pasteboardService: PasteboardService,
    private val pasteReleaseService: PasteReleaseService,
    private val userDataPathProvider: UserDataPathProvider,
) : PasteMenuContributor {

    override fun contribute(pasteData: PasteData): List<PasteMenuAction> =
        if (pasteData.getType() == PasteType.IMAGE_TYPE) {
            listOf(PasteMenuAction(copywriter.getText("extract_text")) { extractText(pasteData) })
        } else {
            emptyList()
        }

    private suspend fun extractText(pasteData: PasteData) {
        val extractText =
            pasteData
                .getPasteItem(ImagesPasteItem::class)
                ?.getFilePaths(userDataPathProvider)
                ?.mapNotNull { path ->
                    ocrModule.extractText(path).getOrNull()
                }?.joinToString(separator = "\n")

        if (!extractText.isNullOrBlank()) {
            val pasteItem = createTextPasteItem(text = extractText)
            // Our own pasteboard writes are not captured, so record the paste directly
            val id =
                pasteReleaseService.createLocalPasteData(
                    appInstanceId = appInfo.appInstanceId,
                    pasteItem = pasteItem,
                    source = AppName,
                )
            pasteboardService.tryWritePasteboard(
                id = id,
                pasteItem = pasteItem,
                localOnly = true,
            )
        }
    }
}
