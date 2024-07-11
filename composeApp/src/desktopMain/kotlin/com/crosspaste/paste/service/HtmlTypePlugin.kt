package com.crosspaste.paste.service

import com.crosspaste.app.AppInfo
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.os.windows.html.HTMLCodec
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.PasteTypePlugin
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.DesktopFileUtils
import com.crosspaste.utils.getCodecsUtils
import io.realm.kotlin.MutableRealm
import java.awt.datatransfer.DataFlavor

class HtmlTypePlugin(private val appInfo: AppInfo) : PasteTypePlugin {

    companion object HtmlTypePlugin {

        const val HTML_ID = "text/html"

        private val codecsUtils = getCodecsUtils()
    }

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
            val md5 = codecsUtils.md5(htmlBytes)
            val relativePath =
                DesktopFileUtils.createPasteRelativePath(
                    appInstanceId = appInfo.appInstanceId,
                    pasteId = pasteId,
                    fileName = "html2Image.png",
                )
            val update: (PasteItem, MutableRealm) -> Unit = { pasteItem, realm ->
                realm.query(HtmlPasteItem::class, "id == $0", pasteItem.id).first().find()?.apply {
                    this.html = html
                    this.relativePath = relativePath
                    this.size = htmlBytes.size.toLong()
                    this.md5 = md5
                }
            }
            pasteCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }

    override fun buildTransferable(
        pasteItem: PasteItem,
        map: MutableMap<PasteDataFlavor, Any>,
    ) {
        pasteItem as HtmlPasteItem
        var currentHtml = pasteItem.html
        if (currentPlatform().isWindows()) {
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
