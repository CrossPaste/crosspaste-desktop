package com.crosspaste.paste.plugin.type

import com.crosspaste.db.paste.PasteType
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItem.Companion.updateExtraInfo
import com.crosspaste.paste.item.PasteItemProperties.BACKGROUND
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.platform.Platform
import com.crosspaste.platform.windows.html.HTMLCodec
import com.crosspaste.plugin.office.OfficeHtmlPlugin
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getHtmlUtils
import kotlinx.serialization.json.put
import java.awt.datatransfer.DataFlavor

class DesktopHtmlTypePlugin(
    private val platform: Platform,
) : HtmlTypePlugin {

    companion object {

        const val HTML_ID = "text/html"

        private val codecsUtils = getCodecsUtils()

        private val htmlUtils = getHtmlUtils()

        private val officeHtmlPlugin = OfficeHtmlPlugin()
    }

    override fun getPasteType(): PasteType = PasteType.HTML_TYPE

    override fun getIdentifiers(): List<String> = listOf(HTML_ID)

    override fun createPrePasteItem(
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
            val htmlBytes = html.encodeToByteArray()
            val hash = codecsUtils.hash(htmlBytes)
            val size = htmlBytes.size.toLong()
            val background = htmlUtils.getBackgroundColor(html)
            val update: (PasteItem) -> PasteItem = { pasteItem ->
                HtmlPasteItem(
                    identifiers = pasteItem.identifiers,
                    hash = hash,
                    size = size,
                    html = html,
                    extraInfo =
                        updateExtraInfo(
                            pasteItem.extraInfo,
                            update = {
                                background?.let {
                                    put(BACKGROUND, background.value.toLong() ushr 32)
                                }
                            },
                        ),
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
        if (platform.isWindows()) {
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
        val html =
            if (start != -1) {
                inputStr.substring(start)
            } else {
                inputStr
            }
        return htmlUtils.ensureHtmlCharsetUtf8(html)
    }

    override fun normalizeHtml(
        html: String,
        source: String?,
    ): String =
        source?.let {
            if (officeHtmlPlugin.match(source)) {
                officeHtmlPlugin.officeNormalizationHTML(html)
            } else {
                html
            }
        } ?: html
}
