package com.crosspaste.paste.plugin.type

import com.crosspaste.app.AppInfo
import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.windows.html.HTMLCodec
import com.crosspaste.plugin.office.OfficeHtmlPlugin
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getFileUtils
import java.awt.datatransfer.DataFlavor

class DesktopHtmlTypePlugin(
    private val appInfo: AppInfo,
) : HtmlTypePlugin {

    companion object {

        const val HTML_ID = "text/html"

        private val codecsUtils = getCodecsUtils()

        private val fileUtils = getFileUtils()

        private val officeHtmlPlugin = OfficeHtmlPlugin()
    }

    override fun getPasteType(): PasteType {
        return PasteType.HTML_TYPE
    }

    override fun getIdentifiers(): List<String> {
        return listOf(HTML_ID)
    }

    override fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        HtmlPasteItem(
            identifiers = listOf(HTML_ID),
            hash = "",
            size = 0,
            html = "",
            relativePath = "",
        ).let {
            pasteCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(
        transferData: Any,
        pasteId: Long,
        itemIndex: Int,
        dataFlavor: PasteDataFlavor,
        dataFlavorMap: Map<String, List<PasteDataFlavor>>,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        if (transferData is String) {
            val html = extractHtml(transferData)
            val htmlBytes = html.toByteArray()
            val hash = codecsUtils.hash(htmlBytes)
            val size = htmlBytes.size.toLong()
            val relativePath =
                fileUtils.createPasteRelativePath(
                    pasteCoordinate =
                        PasteCoordinate(
                            appInstanceId = appInfo.appInstanceId,
                            pasteId = pasteId,
                        ),
                    fileName = "html2Image.png",
                )
            val update: (PasteItem) -> PasteItem = { pasteItem ->
                HtmlPasteItem(
                    identifiers = pasteItem.identifiers,
                    hash = hash,
                    size = size,
                    html = html,
                    relativePath = relativePath,
                )
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }

    override fun buildTransferable(
        pasteItem: PasteItem,
        singleType: Boolean,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as HtmlPasteItem
        var currentHtml = pasteItem.html
        if (getPlatform().isWindows()) {
            currentHtml = String(HTMLCodec.convertToHTMLFormat(currentHtml))
        }
        map[DataFlavor.selectionHtmlFlavor.toPasteDataFlavor()] = currentHtml
        map[DataFlavor.fragmentHtmlFlavor.toPasteDataFlavor()] = currentHtml
        map[DataFlavor.allHtmlFlavor.toPasteDataFlavor()] = currentHtml
    }

    private fun extractHtml(inputStr: String): String {
        // this is microsoft html format
        // https://learn.microsoft.com/zh-cn/windows/win32/dataxchg/html-clipboard-format?redirectedfrom=MSDN
        return if (inputStr.startsWith("Version:")) {
            extractHtmlFromMicrosoftHtml(inputStr)
        } else {
            inputStr
        }
    }

    private fun extractHtmlFromMicrosoftHtml(inputStr: String): String {
        val start = inputStr.indexOfFirst { it == '<' }
        return if (start != -1) {
            inputStr.substring(start)
        } else {
            inputStr
        }
    }

    override fun normalizeHtml(
        html: String,
        source: String?,
    ): String {
        return source?.let {
            if (officeHtmlPlugin.match(source)) {
                officeHtmlPlugin.officeNormalizationHTML(html)
            } else {
                html
            }
        } ?: html
    }
}
