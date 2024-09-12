package com.crosspaste.paste.plugin.type

import com.crosspaste.app.AppInfo
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.windows.html.HTMLCodec
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getFileUtils
import io.realm.kotlin.MutableRealm
import java.awt.datatransfer.DataFlavor

class HtmlTypePlugin(private val appInfo: AppInfo) : PasteTypePlugin {

    companion object HtmlTypePlugin {

        const val HTML_ID = "text/html"

        private val codecsUtils = getCodecsUtils()
    }

    private val fileUtils = getFileUtils()

    override fun getPasteType(): Int {
        return PasteType.HTML
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
        HtmlPasteItem().apply {
            this.identifier = identifier
        }.let {
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
            val relativePath =
                fileUtils.createPasteRelativePath(
                    pasteCoordinate =
                        PasteCoordinate(
                            appInstanceId = appInfo.appInstanceId,
                            pasteId = pasteId,
                        ),
                    fileName = "html2Image.png",
                )
            val update: (PasteItem, MutableRealm) -> Unit = { pasteItem, realm ->
                realm.query(HtmlPasteItem::class, "id == $0", pasteItem.id).first().find()?.apply {
                    this.html = html
                    this.relativePath = relativePath
                    this.size = htmlBytes.size.toLong()
                    this.hash = hash
                }
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
}
