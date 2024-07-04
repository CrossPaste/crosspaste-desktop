package com.crosspaste.paste.service

import com.crosspaste.app.AppInfo
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteItemService
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.utils.DesktopFileUtils
import com.crosspaste.utils.getEncryptUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.MutableRealm
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class HtmlItemService(appInfo: AppInfo) : PasteItemService(appInfo) {

    companion object HtmlItemService {

        const val HTML_ID = "text/html"

        private val encryptUtils = getEncryptUtils()
    }

    val logger = KotlinLogging.logger {}

    override fun getIdentifiers(): List<String> {
        return listOf(HTML_ID)
    }

    override fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
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
        dataFlavor: DataFlavor,
        dataFlavorMap: Map<String, List<DataFlavor>>,
        transferable: Transferable,
        pasteCollector: PasteCollector,
    ) {
        if (transferData is String) {
            val html = extractHtml(transferData)
            val htmlBytes = html.toByteArray()
            val md5 = encryptUtils.md5(htmlBytes)
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
