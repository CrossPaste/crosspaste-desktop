package com.clipevery.clip.service

import com.clipevery.app.AppFileType
import com.clipevery.clip.ClipCollector
import com.clipevery.clip.ClipItemService
import com.clipevery.clip.DesktopChromeService
import com.clipevery.clip.item.HtmlClipItem
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.DesktopOneFilePersist
import com.clipevery.utils.DesktopFileUtils
import com.clipevery.utils.md5ByString
import io.realm.kotlin.MutableRealm
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class HtmlItemService: ClipItemService {

    companion object HtmlItemService {

        const val HTML_ID = "text/html"

        val chromeService = DesktopChromeService
    }

    override fun getIdentifiers(): List<String> {
        return listOf(HTML_ID)
    }

    override fun createPreClipItem(
        clipId: Int,
        itemIndex: Int,
        identifier: String,
        transferable: Transferable,
        clipCollector: ClipCollector
    ) {
        HtmlClipItem().apply {
            this.identifier = identifier
        }.let {
            clipCollector.preCollectItem(itemIndex, this::class, it)
        }
    }

    override fun doLoadRepresentation(transferData: Any,
                                      clipId: Int,
                                      itemIndex: Int,
                                      dataFlavor: DataFlavor,
                                      dataFlavorMap: Map<String, List<DataFlavor>>,
                                      transferable: Transferable,
                                      clipCollector: ClipCollector) {
        if (transferData is String) {
            val html = extractHtml(transferData)
            val md5 = md5ByString(html)
            val relativePath = DesktopFileUtils.createClipRelativePath(clipId, "html2Image.png")
            chromeService.html2Image(html)?.let {
                val basePath = DesktopPathProvider.resolve(appFileType = AppFileType.HTML)
                val imagePath = DesktopPathProvider.resolve(basePath, relativePath, isFile = true)
                DesktopOneFilePersist(imagePath).saveBytes(it)
            }
            val update: (ClipAppearItem, MutableRealm) -> Unit = { clipItem, realm ->
                realm.query(HtmlClipItem::class).query("id == $0", clipItem.id).first().find()?.apply {
                    this.html = html
                    this.relativePath = relativePath
                    this.md5 = md5
                }
            }
            clipCollector.updateCollectItem(itemIndex, this::class, update)
        }
    }

    private fun extractHtml(inputStr: String): String {
        val pattern = Regex("<([a-zA-Z]+).*?>.*?</\\1>", RegexOption.DOT_MATCHES_ALL)

        val matches = pattern.findAll(inputStr)

        val match = matches.firstOrNull()

        return match?.value ?: inputStr
    }
}