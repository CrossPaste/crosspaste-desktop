package com.crosspaste.paste.service

import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.paste.PasteCollector
import com.crosspaste.paste.PasteDataFlavor
import com.crosspaste.paste.PasteDataFlavors.URL_FLAVOR
import com.crosspaste.paste.PasteTransferable
import com.crosspaste.paste.PasteTypePlugin
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.toPasteDataFlavor
import com.crosspaste.utils.getCodecsUtils
import io.realm.kotlin.MutableRealm
import java.net.URL

class UrlTypePlugin : PasteTypePlugin {

    companion object UrlTypePlugin {
        const val URL = "application/x-java-url"

        private val codecsUtils = getCodecsUtils()
    }

    override fun getPasteType(): Int {
        return PasteType.URL
    }

    override fun getIdentifiers(): List<String> {
        return listOf(URL)
    }

    override fun createPrePasteItem(
        pasteId: Long,
        itemIndex: Int,
        identifier: String,
        pasteTransferable: PasteTransferable,
        pasteCollector: PasteCollector,
    ) {
        UrlPasteItem().apply {
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
            val urlBytes = transferData.toByteArray()
            val md5 = codecsUtils.md5(urlBytes)
            val update: (PasteItem, MutableRealm) -> Unit = { pasteItem, realm ->
                realm.query(UrlPasteItem::class, "id == $0", pasteItem.id).first().find()?.apply {
                    this.url = transferData
                    this.size = urlBytes.size.toLong()
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
        pasteItem as UrlPasteItem
        map[URL_FLAVOR.toPasteDataFlavor()] = URL(pasteItem.url)
    }
}
